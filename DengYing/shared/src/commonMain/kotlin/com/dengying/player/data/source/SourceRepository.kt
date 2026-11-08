package com.dengying.player.data.source

import com.dengying.player.data.net.HlsUtil
import com.dengying.player.data.net.NetPriority
import com.dengying.player.data.source.adapters.CmsAdapter
import com.dengying.player.data.source.adapters.DubokuAdapter
import com.dengying.player.data.source.adapters.GuapiAdapter
import com.dengying.player.data.source.adapters.JuzhiAdapter
import com.dengying.player.data.source.adapters.MinoAdapter
import com.dengying.player.data.source.adapters.ShanzhaAdapter
import com.dengying.player.data.source.adapters.Tv189Adapter
import com.dengying.player.data.source.adapters.WebAdapter
import com.dengying.player.data.source.adapters.WencaiAdapter
import com.dengying.player.domain.page.ConcurrencyGate
import com.dengying.player.core.HealthSignal
import com.dengying.player.domain.page.ListBatch
import com.dengying.player.domain.page.PageBudget
import com.dengying.player.domain.page.PageClock
import com.dengying.player.core.RequestOutcome
import com.dengying.player.domain.source.SourceHealthRules
import com.dengying.player.domain.source.SourceOverrideRules
import com.dengying.player.domain.search.TitleKey
import com.dengying.player.core.isSourceHealthy
import com.dengying.player.core.shouldFailover
import com.dengying.player.core.toHealthSignal
import com.dengying.player.core.CmsClass
import com.dengying.player.core.HomeSection
import com.dengying.player.core.HomeTab
import com.dengying.player.core.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import com.dengying.player.platform.Clock
import com.dengying.player.platform.PlatformLock
import com.dengying.player.platform.ioDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import com.dengying.player.core.CmsSource
import com.dengying.player.core.CmsSources
import com.dengying.player.core.FetchResult
import com.dengying.player.core.PlayResolveResult
import com.dengying.player.core.SourceProtocol

/**
 * 源接入聚合客户端（多协议分派）
 *
 * 协议分派：
 *  - `Cms` / `Api2233` → [CmsAdapter]
 *  - `Mino`            → [MinoAdapter]
 *  - `Wencai`          → [WencaiAdapter]
 *  - `Duboku`          → [DubokuAdapter]
 *  - `Html`            → [WebAdapter]
 *  - `Juzhi`/`Guapi`/`Shanzha` → 同名适配器
 *
 * 设计要点：
 *  - 单次请求独立超时，失败不影响其他源
 *  - 并发经全局闸门（[com.dengying.player.domain.page.ConcurrencyGate]），不在此层各自为限
 *  - 对非 JSON 返回做防御（部分源会返回 HTML 错误页）
 *  - 分页键按 `caps` 能力挑选源：不支持分类的源不参与分类路径
 *
 * 【状态说明】健康表与会话序号是**模块级可变状态**，由 [SourceRepository] 单例持有。
 * 这不是"全局变量随意用"——它与产品的关键契约绑定：
 *  - 健康分**会话级、不持久化**（避免把一次弱网故障固化为长期惩罚）
 *  - 搜索序号**跨调用递增**（用于丢弃过期结果）
 */
class SourceRepository {

    /**
     * 仓库自有作用域：**只用于"不挡路"的后台补齐**。
     *
     * 【为什么必须有】原写法是
     * `coroutineScope { launch { runCatching { resolveTypeIds(sources, categoryName, 999) } } }`。
     * 注释写的是"后台补齐其余 tier"，但 `coroutineScope` 会**等所有子协程结束**才返回，
     * 于是它变成了同步前置步骤：先把全部 tier、全部源的分类表都解析完，才去拉列表。
     *
     * 真机探针抓到 `index-load cat=电影 elapsed=11747 firstBatch=11746` 正是这个形态——
     * 首批与最终相差 1ms，说明首批门槛从头到尾没被单独满足过。
     *
     * 用独立作用域 `launch` 出去，父协程立即返回去取列表；补齐任务自己跑完写缓存。
     * 用 [SupervisorJob] 而非普通 Job：补齐失败不该连坐整个仓库。
     */
    private val bgScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // ---------------- FR-601：源启用覆盖层 ----------------

    /**
     * 覆盖层。由 Store 在初始化时载入、写入时更新。
     *
     * 【为什么这里是可变字段而不是每次读存储】
     * 聚合路径的源过滤是**同步**的，而读存储是挂起的。若每次过滤都 await，
     * 整条聚合链要改成异步，波及面远大于收益。故缓存一份，读取走内存，
     * 写入时两处一起改。
     */
    @kotlin.concurrent.Volatile
    var override: SourceOverrideRules.Payload = SourceOverrideRules.emptyPayload()

    /** FR-601：源是否启用——**统一判定入口**。全项目只经本函数判定。 */
    fun isSourceEnabled(s: CmsSource): Boolean =
        SourceOverrideRules.enabledOf(override, s.key, s.enabled)

    /** FR-601：应用覆盖层后的启用源列表（顺序保持清单原序） */
    fun effectiveSources(): List<CmsSource> = CmsSources.ALL.filter { isSourceEnabled(it) }

    /** FR-601：应用覆盖层后的启用源数 */
    fun enabledSourceCount(): Int =
        SourceOverrideRules.effectiveEnabledCount(override, CmsSources.baselines())

    // ---------------- FR-107：源健康表（会话级，不持久化） ----------------

    /**
     * 健康表。**读写统一经 [healthLock]**。
     *
     * 原实现用 `ConcurrentHashMap.compute` 做原子读改写；跨平台层没有等价原语，
     * 故改为普通 HashMap + 锁。锁内只有一次极短的读改写，不跨挂起点，
     * 不会死锁也不会把并发路径串行化。
     */
    private val health = HashMap<String, SourceHealthRules.Health>()
    private val healthLock = PlatformLock()

    private fun healthOf(key: String): SourceHealthRules.Health? = healthLock.withLock { health[key] }

    private fun noteOutcome(key: String, signal: HealthSignal) {
        val outcome = when (signal) {
            HealthSignal.Ok -> SourceHealthRules.Outcome.Ok
            HealthSignal.SuccessNoData -> SourceHealthRules.Outcome.SuccessNoData
            HealthSignal.Failed -> SourceHealthRules.Outcome.Failed
        }
        healthLock.withLock {
            val cur = health[key]
            health[key] = SourceHealthRules.record(cur ?: SourceHealthRules.newHealth(key), outcome)
        }
    }

    /** OPS-005：导出健康视图行（只含**启用**源，含尚无记录的） */
    fun sourceHealthRows(): List<SourceHealthRules.Row> =
        CmsSources.ALL.filter { isSourceEnabled(it) }
            .map { healthOf(it.key) ?: SourceHealthRules.newHealth(it.key) }
            .let { SourceHealthRules.toRows(it) }

    /**
     * OPS-005 + FR-601：导出**全部**源的健康视图行（含停用源）。
     *
     * 与 [sourceHealthRows] 的差别：那个只含启用源，供"当前谁在参与聚合"。
     * 本函数含**停用源**——FR-601 要求用户能在应用内**查看并启用**源，
     * 若列表只显示启用源，停用过的源就再也找不回来（无法重新启用），
     * 那样"启用停用"只是单向的。
     */
    fun allSourceHealthRows(): List<SourceHealthRules.Row> =
        SourceHealthRules.toRows(
            CmsSources.ALL.map { healthOf(it.key) ?: SourceHealthRules.newHealth(it.key) }
        )

    /** FR-107：是否存在"源全部可疑"的情形（为真时调用方应**兜底全试**） */
    fun allSourcesSuspicious(): Boolean = SourceHealthRules.needFallbackAll(
        CmsSources.ALL.filter { isSourceEnabled(it) }
            .mapNotNull { healthOf(it.key) }
    )

    // ---------------- 单源能力 ----------------

    /** 单源不支持某能力时的统一失败结果 */
    private fun unsupported(source: CmsSource, what: String) = FetchResult(
        // 能力缺失不是请求失败，但也不是"源没数据"——对用户而言这条源本次没帮上忙，
        // 故归 RequestFailed（会触发换源尝试其它源）。
        outcome = RequestOutcome.RequestFailed,
        sourceName = source.name,
        err = "该源不支持$what",
    )

    /** 单源：按分类取列表（CMS 家族与 Tv189 支持分类） */
    suspend fun fetchList(source: CmsSource, typeId: Int?, page: Int): FetchResult {
        if (!source.caps.category) return unsupported(source, "分类浏览")
        return when (source.protocol) {
            SourceProtocol.Cms, SourceProtocol.Api2233 -> CmsAdapter.list(source, typeId, page)
            SourceProtocol.Tv189 -> Tv189Adapter.list(source, typeId, page)
            else -> unsupported(source, "分类浏览")
        }
    }

    /** 单源：搜索（按协议分派；命中即用，死源快速出局） */
    suspend fun search(source: CmsSource, keyword: String): FetchResult {
        if (!source.caps.search) return unsupported(source, "搜索")
        return when (source.protocol) {
            SourceProtocol.Api2233 -> CmsAdapter.api2233Search(source, keyword)
            SourceProtocol.Mino -> MinoAdapter.search(source, keyword)
            SourceProtocol.Wencai -> WencaiAdapter.search(source, keyword)
            SourceProtocol.Duboku -> DubokuAdapter.search(source, keyword)
            SourceProtocol.Html -> source.rule?.let { WebAdapter.search(source, keyword, it) }
                ?: unsupported(source, "搜索")
            SourceProtocol.Juzhi -> JuzhiAdapter.search(source, keyword)
            SourceProtocol.Guapi -> GuapiAdapter.search(source, keyword)
            SourceProtocol.Shanzha -> ShanzhaAdapter.search(source, keyword)
            SourceProtocol.Cms -> CmsAdapter.search(source, keyword)
            // 2026-11-08 起走 keywordSearchCtyun（programsearch.keyword 实测无效，
            // 已弃用）；comeFrom=NEW_CPMS 只搜自营可播库
            SourceProtocol.Tv189 -> Tv189Adapter.search(source, keyword)
        }
    }

    /** 取详情（按 ID / 原站寻址串） */
    suspend fun fetchDetail(source: CmsSource, vodId: Long, vodUri: String = ""): VideoItem? =
        when (source.protocol) {
            SourceProtocol.Mino -> MinoAdapter.detail(source, vodId)
            SourceProtocol.Wencai -> WencaiAdapter.detail(source, vodId)
            SourceProtocol.Tv189 -> Tv189Adapter.detail(source, vodId, vodUri)
            SourceProtocol.Duboku -> DubokuAdapter.detail(source, vodId)
            SourceProtocol.Html -> source.rule?.let { WebAdapter.detail(source, vodId, it, vodUri) }
            SourceProtocol.Juzhi -> JuzhiAdapter.detail(source, vodId)
            SourceProtocol.Guapi -> GuapiAdapter.detail(source, vodId)
            SourceProtocol.Cms, SourceProtocol.Api2233 -> CmsAdapter.detail(source, vodId)
            SourceProtocol.Shanzha -> null   // 该源只支持搜索（上游 502）
        }

    /**
     * 播放地址换取（token 型源唯一入口）。
     *
     * 直链型源直接返回入参地址；token 型源在此换出最终媒体地址与所需请求头。
     */
    suspend fun resolveSourcePlay(
        source: CmsSource,
        vodId: Long,
        token: String,
        from: String,
    ): PlayResolveResult {
        if (!source.caps.token) return PlayResolveResult(url = token, headers = "")
        return when (source.protocol) {
            SourceProtocol.Mino -> MinoAdapter.resolvePlay(source, vodId, token, from)
            SourceProtocol.Wencai -> WencaiAdapter.resolvePlay(source, vodId, token, from)
            SourceProtocol.Tv189 -> Tv189Adapter.resolvePlay(source, vodId, token, from)
            SourceProtocol.Duboku -> DubokuAdapter.resolvePlay(source, vodId, token, from)
            SourceProtocol.Html -> source.rule?.let { WebAdapter.resolvePlay(source, token, it) }
                ?: PlayResolveResult()
            SourceProtocol.Juzhi -> JuzhiAdapter.resolvePlay(source, vodId, token, from)
            SourceProtocol.Guapi -> GuapiAdapter.resolvePlay(source, vodId, token, from)
            else -> PlayResolveResult(url = token, headers = "")
        }
    }

    // ---------------- 聚合搜索（FR-201/202/203/204/207/208） ----------------

    /** 搜索会话序号：每次 [searchAll] 递增；迟到回调校验序号，过期即丢弃 */
    private var searchSessionSeq: Int = 0

    /**
     * 多源聚合搜索（流式）。
     *
     * 架构：
     *  - 启用源**全并发**（不再分批接力，一批死源不拖累其他批）
     *  - 每源返回即经 `onPartial` 回调上屏（tier 靠前源 ~1s 出结果，体感秒搜）
     *  - 总预算 [PageBudget.PAGE_BUDGET_MS] 兜底：到点用已有结果返回
     *
     * @param onPartial 部分结果回调（已折叠去重、按到达顺序累积）
     */
    suspend fun searchAll(
        sources: List<CmsSource>,
        keyword: String,
        onPartial: ((List<VideoItem>) -> Unit)? = null,
    ): List<VideoItem> = coroutineScope {
        val session = ++searchSessionSeq

        val enabled = sources
            .filter { isSourceEnabled(it) && it.caps.search }
            .sortedWith(
                compareBy<CmsSource> { it.tier }
                    // FR-107：同 tier 内按健康分纠偏（不推翻人工 tier 先验）
                    .thenByDescending { healthOf(it.key)?.score ?: 0 }
                    .thenBy { it.speed }
            )

        val merged = ArrayList<VideoItem>()
        val seen = HashSet<String>()
        val clock = PageClock(Clock.nowMs())

        val tasks = enabled.map { s ->
            async {
                val r = try {
                    search(s, keyword)
                } catch (e: Exception) {
                    FetchResult(RequestOutcome.RequestFailed, sourceName = s.name)
                }
                // FR-107：记录本次结果（会话级，不持久化）
                noteOutcome(s.key, r.outcome.toHealthSignal())

                // 迟到守卫：会话已过期（新搜索已发起）→ 本结果作废，不 ingest 不回调
                if (session != searchSessionSeq) return@async

                var added = 0
                if (r.hasData) {
                    for (it in r.items) {
                        // FR-204：折叠键统一由 TitleKey 构造
                        val k = TitleKey.ofRaw(it.vod_name, it.vod_year)
                        if (seen.contains(k)) continue
                        seen.add(k)
                        merged.add(it)
                        added++
                    }
                }
                if (added > 0) onPartial?.invoke(merged.toList())
            }
        }

        // 总预算兜底：到点不再等剩余源（其结果丢弃，避免无限等死源）
        val all = async { tasks.awaitAll() }
        val timeout = async {
            delay(clock.remaining(Clock.nowMs()))
        }
        kotlinx.coroutines.selects.select<Unit> {
            all.onAwait { }
            timeout.onAwait { }
        }
        merged
    }

    /**
     * 带完成度的全源搜索（NFR-003）。
     *
     * 与 [searchAll] 同一套聚合逻辑，差别只有一处：把**完成度**一并返回。
     * 原 [searchAll] 的返回值与「所有源都跑完」完全一样，调用方无从判断结果
     * 是否完整，也就无法向用户说明。
     */
    suspend fun searchAllBudgeted(
        sources: List<CmsSource>,
        keyword: String,
        nowMs: Long,
        onPartial: ((List<VideoItem>) -> Unit)? = null,
    ): PageBudget.Budgeted<VideoItem> {
        val clock = PageClock(nowMs)
        val items = searchAll(sources, keyword, onPartial)
        val used = clock.elapsed(Clock.nowMs())
        // 用真实耗时判定，而不是另设标志位——标志位会与实际等待时长脱节
        val outcome =
            if (used >= clock.getBudgetMs()) PageBudget.Outcome.Expired else PageBudget.Outcome.Complete
        return PageBudget.Budgeted(items, outcome, used)
    }

    // ---------------- 分类表缓存（FR-103） ----------------

    /** 分类表缓存项。public 是必要的——它出现在 [classCacheReader] 的公开签名里。 */
    data class ClassCacheEntry(val classes: List<CmsClass>, val time: Long)

    /**
     * 分类表内存缓存 / 在途合并表 / 失败时间戳，三者共用 [classLock]。
     *
     * 原实现依赖 ConcurrentHashMap 的原子性；跨平台层改用普通 HashMap + 锁。
     * **`await` 一律在锁外**——持锁等待会把其他源的分类表请求串行化。
     */
    private val classCache = HashMap<String, ClassCacheEntry>()
    private val inflightClass = HashMap<String, Deferred<List<CmsClass>>>()
    private val classLock = PlatformLock()

    /**
     * 跨源候选搜索的进程级缓存（片名归一化 → 候选列表）。
     *
     * 详情页预取与播放链兜底（FR-610 试看链全片优先）先后用同一片名调用
     * [findAltSources]，每次全量并发搜 12+ 源要 2-5 秒——首搜结果缓存复用，
     * 播放链只剩"试候选"一段。只在搜到非空结果时缓存（失败下次重搜）。
     */
    private val altSearchCache = HashMap<String, List<AltCandidate>>()
    private val inflightAlt = HashMap<String, Deferred<List<AltCandidate>>>()
    private val altSearchLock = PlatformLock()

    /**
     * 分类表**拉取失败**的时间戳（负缓存）。
     *
     * 【为什么需要负缓存】原实现只缓存「拉到的」分类表，拉失败什么都不留。
     * 于是死源（真机实测 jyzy / huyazy 的 `ac=list` 完全不响应）每次进入分类
     * 都要重新等满 3s 超时：一次进首页、每次切分类、每次翻页都白等 2×3s。
     * 健康分能把它排到后面，但**排在后面不等于不试**——只要 warmup 没凑够就
     * 还会轮它，翻页实测 `index-more elapsed=4398`（3s 超时 + 1.4s 列表）就是它。
     *
     * 记下失败时刻后，[failTtl] 内直接返回空表，不再发请求。
     * 取 60s 而非与 [cacheTtl] 相同的 30min：源站故障常在几分钟内恢复，
     * 负缓存过长会把「临时抽风」变成「半分钟内不可用」。60s 也足够覆盖
     * 「进首页 → 连切几个分类 → 翻几页」这一整串用户操作。
     */
    private val classFailAt = HashMap<String, Long>()

    private val cacheTtl = 30 * 60 * 1000L

    /** 分类表拉取失败后的负缓存时长 */
    private val failTtl = 60 * 1000L

    /** 磁盘缓存回调：由 Store 注入（避免 data.source 依赖 data.store 的具体实现） */
    var classCacheReader: (suspend () -> Map<String, ClassCacheEntry>)? = null
    var classCacheWriter: (suspend (Map<String, ClassCacheEntry>) -> Unit)? = null

    /** 取分类表（ac=list）：内存 → 磁盘 → 网络；并发请求按源合并 */
    suspend fun fetchClasses(source: CmsSource): List<CmsClass> = coroutineScope {
        if (!source.caps.category) return@coroutineScope emptyList()

        val now = Clock.nowMs()
        val cached = classLock.withLock {
            val e = classCache[source.key]
            if (e != null && now - e.time < cacheTtl && e.classes.isNotEmpty()) e.classes else null
        }
        if (cached != null) return@coroutineScope cached

        val diskHit = classCacheReader?.invoke()?.get(source.key)
        if (diskHit != null && now - diskHit.time < cacheTtl && diskHit.classes.isNotEmpty()) {
            classLock.withLock { classCache[source.key] = diskHit }
            return@coroutineScope diskHit.classes
        }

        // 负缓存：刚失败过的源不再重试，直接当作「没有分类表」。
        // 放在磁盘缓存之后、在途合并之前——磁盘里有过期旧值时优先用旧值。
        val recentlyFailed = classLock.withLock {
            val at = classFailAt[source.key]
            at != null && now - at < failTtl
        }
        if (recentlyFailed) return@coroutineScope emptyList()

        // 同一源的在途请求合并（首屏多处同时触发时只发一次）。
        // await 在锁外：持锁等待会把其他源的分类表请求串行化。
        classLock.withLock { inflightClass[source.key] }
            ?.let { return@coroutineScope it.await() }

        val task = async {
            // Tv189 的分类表是静态映射（categoryId 为前端硬编码常量），不发网络请求；
            // 仍走缓存路径写入，与其他源行为一致（负缓存/TTL 语义照常生效）
            val classes = if (source.protocol == SourceProtocol.Tv189) {
                Tv189Adapter.classes()
            } else {
                CmsAdapter.classes(source)
            }
            if (classes.isNotEmpty()) {
                // 快照在锁内取，磁盘写入在锁外做——写盘是挂起操作，不能持锁。
                val snapshot = classLock.withLock {
                    classFailAt.remove(source.key)
                    classCache[source.key] = ClassCacheEntry(classes, Clock.nowMs())
                    classCache.toMap()
                }
                classCacheWriter?.invoke(snapshot)
            } else if (diskHit != null) {
                // 拉取失败时若有过期缓存，仍返回旧值，避免抖动
                classLock.withLock { classCache[source.key] = diskHit }
                return@async diskHit.classes
            } else {
                // 记下失败时刻：后续 failTtl 内不再为这个源付超时代价
                classLock.withLock { classFailAt[source.key] = Clock.nowMs() }
            }
            classes
        }
        classLock.withLock { inflightClass[source.key] = task }
        try {
            task.await()
        } finally {
            classLock.withLock { inflightClass.remove(source.key) }
        }
    }

    /** 清空分类缓存（内存；磁盘清理走写入器传空表） */
    suspend fun clearClassCache() {
        classLock.withLock {
            classCache.clear()
            inflightClass.clear()
            // 负缓存一并清掉：下拉刷新是用户主动要求「重新试一次」，
            // 若保留负缓存，刷新后仍然看不到刚恢复的源，等于刷新无效。
            classFailAt.clear()
        }
        classCacheWriter?.invoke(emptyMap())
    }

    // ---------------- 首页版块缓存（tv189 jsondata） ----------------

    /**
     * 版块数据的内存 TTL 缓存，key = "源key|path"，TTL 节奏与 [cacheTtl] 一致。
     *
     * 【为什么比分类缓存简单】版块请求只在「推荐」页选中某个 tab 时发出，
     * 不像分类表那样首屏多处并发触发，不需要在途合并；也不落磁盘——
     * 它是运营位数据（轮播图/推荐位），过期重拉一次即可，缓存它收益很小。
     * 失败负缓存照旧：WAF 间歇性拦截时不至于每次切 tab 都白等一次超时。
     */
    private data class HomeCacheEntry(val tabs: List<HomeTab>, val sections: List<HomeSection>, val time: Long)
    private val homeCache = HashMap<String, HomeCacheEntry>()
    private val homeFailAt = HashMap<String, Long>()
    private val homeLock = PlatformLock()

    /** 导航 tab（内存缓存，无磁盘层；force=true 跳过缓存与负缓存直拉网络） */
    suspend fun fetchHomeTabs(source: CmsSource, force: Boolean = false): List<HomeTab> {
        if (!source.caps.home) return emptyList()
        val now = Clock.nowMs()
        val key = "${source.key}|"
        if (!force) {
            val cached = homeLock.withLock {
                val e = homeCache[key]
                if (e != null && now - e.time < cacheTtl && e.tabs.isNotEmpty()) e.tabs else null
            }
            if (cached != null) return cached
        }
        val tabs = Tv189Adapter.homeTabs(source)
        homeLock.withLock {
            if (tabs.isNotEmpty()) homeCache[key] = HomeCacheEntry(tabs, emptyList(), Clock.nowMs())
            else homeFailAt[key] = Clock.nowMs()
        }
        return tabs
    }

    /** 首页版块（内存 TTL + 失败负缓存；force=true 跳过两者直拉网络） */
    suspend fun fetchHomeSections(source: CmsSource, path: String, force: Boolean = false): List<HomeSection> {
        if (!source.caps.home || path.isEmpty()) return emptyList()
        val key = "${source.key}|$path"
        val now = Clock.nowMs()
        if (!force) {
            val cached = homeLock.withLock {
                val e = homeCache[key]
                if (e != null && now - e.time < cacheTtl && e.sections.isNotEmpty()) e.sections else null
            }
            if (cached != null) return cached
            // 负缓存：刚拉失败过的 tab 不再立刻重试（WAF 间歇拦截时不白等超时）
            val recentlyFailed = homeLock.withLock {
                val at = homeFailAt[key]
                at != null && now - at < failTtl
            }
            if (recentlyFailed) return emptyList()
        }
        val sections = Tv189Adapter.homeSections(source, path)
        homeLock.withLock {
            if (sections.isNotEmpty()) homeCache[key] = HomeCacheEntry(emptyList(), sections, Clock.nowMs())
            else homeFailAt[key] = Clock.nowMs()
        }
        return sections
    }

    /**
     * 单源分类名 → type_id 解析（精确 → 别名 → 双向包含）。
     * 命中后收集该分类下的**全部子分类**——顶级分类常常是空壳，量在子分类里。
     */
    private suspend fun resolveOne(
        source: CmsSource,
        categoryName: String,
        aliases: List<String>,
    ): TypeIdMap? {
        val classes = fetchClasses(source)
        if (classes.isEmpty()) {
            // FR-107：分类表拉不到 = 该源本次帮不上忙，记一次失败。
            // 改动前**只有搜索路径**记录健康分，分类路径完全不记，
            // 于是死源永远不会被判可疑，每次进首页都重新等它超时。
            noteOutcome(source.key, HealthSignal.Failed)
            return null
        }

        var hit = classes.firstOrNull { it.type_name == categoryName }
        if (hit == null) {
            hit = classes.firstOrNull { c -> aliases.any { it == c.type_name } }
        }
        if (hit == null) {
            hit = classes.firstOrNull { c ->
                aliases.any { a -> c.type_name.contains(a) || a.contains(c.type_name) }
            }
        }
        if (hit == null) {
            // 分类表拿到了但没有这个分类：不是故障（delta=0），只记样本
            noteOutcome(source.key, HealthSignal.SuccessNoData)
            return null
        }
        noteOutcome(source.key, HealthSignal.Ok)

        val ids = ArrayList<Int>()
        ids += hit.type_id
        for (c in classes) {
            if (c.type_pid != null && c.type_pid == hit.type_id && c.type_id != hit.type_id) {
                ids += c.type_id
            }
        }
        return TypeIdMap(source, hit.type_id, ids)
    }

    /**
     * 按分类名在多源中查找匹配的 type_id。
     *
     * 分层并发（tier 1 → 3），**层间流水线**：某层首个命中后立即并发启动下一层，
     * 不等同层慢源跑完——tier 内死源只拖自己，不拖层级推进。命中数 ≥ warmupCount
     * 即提前返回，冷启动不再等全部源。
     *
     * @param onMapped 每命中一个映射就回调（运行在解析协程内，必须轻量）。
     * 供 [fetchByCategory] 做「映射→列表」流水线：首个映射到位即开始拉列表，
     * 不必等 [warmupCount] 个映射凑齐。
     */
    suspend fun resolveTypeIds(
        sources: List<CmsSource>,
        categoryName: String,
        warmupCount: Int = 3,
        /** 解析预算截止时刻（已有至少一条映射时生效）。到点即用已解析到的映射收口 */
        deadlineMs: Long = Long.MAX_VALUE,
        /** 无进展兜底：一条映射都没拿到时的硬截止。防止全挂时无限等 */
        noProgressDeadlineMs: Long = Long.MAX_VALUE,
        onMapped: ((TypeIdMap) -> Unit)? = null,
    ): List<TypeIdMap> = coroutineScope {
        val enabled = sources.filter { isSourceEnabled(it) && it.caps.category }
        val tiers = ArrayList<MutableList<CmsSource>>()
        for (s in enabled) {
            val t = s.tier
            while (tiers.size < t) tiers.add(ArrayList())
            tiers[t - 1].add(s)
        }
        val aliases = CmsSources.CATEGORY_ALIAS[categoryName] ?: listOf(categoryName)
        val out = ArrayList<TypeIdMap>()

        // 【层间流水线，不是层间串行】
        //
        // 原实现是**整层屏障**：tier1 里 hhzy 1.4s 命中后，还得等 jyzy/huyazy
        // 跑满 3s 超时才轮到 tier2——真机实测 firstBatch 因此从 4.8s（warmup=1
        // 形态）涨回 8s（warmup=3），层间串行把 warmup=3 的全部代价都付在了
        // 首屏关键路径上。
        //
        // 改成：某层**首个命中**后即启动下一层（tier 优先序仍保留——空手时不
        // 越层），所有在飞请求统一 select 收集，够数即收口。死源超时自然完成，
        // 顺带写健康分与负缓存，不影响别的源。
        val running = ArrayList<Deferred<TypeIdMap?>>()
        var tierIdx = 0
        try {
            while (true) {
                // 推进启动（每轮最多放一层）：手里已有命中（或当前无在飞请求）
                // 时才放下一层进场，保证「tier1 全挂时不问 tier2 要首屏」的
                // 既有优先序；用 if 而非 while——一次放行全部剩余 tier 会让
                // tier3 的分类表请求在首屏关键路径上抢闸门配额。
                if (tierIdx < tiers.size && (out.isNotEmpty() || running.isEmpty())) {
                    val ordered = tiers[tierIdx].sortedWith(
                        compareByDescending<CmsSource> { healthOf(it.key)?.score ?: 0 }
                            .thenBy { it.speed }
                    )
                    for (s in ordered) {
                        running += async { resolveOne(s, categoryName, aliases) }
                    }
                    tierIdx++
                }
                if (running.isEmpty() || out.size >= warmupCount) break

                // 【预算只在"手里已经有东西"时才生效】
                // 若一条映射都还没拿到，掐掉等待等于把"慢"变成"错"：
                // fetchByCategory 会返回「未找到该分类」，用户看到的是错误页。
                // 故一条都没有时不受 deadlineMs 约束——但受 [noProgressDeadlineMs]
                // 硬底约束，全挂时错误页也要在有限时间内出现。
                val cut = if (out.isEmpty()) noProgressDeadlineMs else deadlineMs
                val left = cut - Clock.nowMs()
                if (left <= 0) break

                // 等**最早完成的那个**。完成 ≠ 命中：resolveOne 对失败/无该分类
                // 返回 null，那是「这个源没帮上忙」，移出继续等别的源即可；
                // 只有预算到点（withTimeoutOrNull 整体返回 null）才收口。
                // ——旧实现把两种 null 混为一谈，源失败会误触 break、
                // 连锁取消同层其他在飞源。
                val timedOut = withTimeoutOrNull(left) {
                    select<Boolean> {
                        running.forEach { d ->
                            d.onAwait { r ->
                                if (r != null) {
                                    out += r
                                    onMapped?.invoke(r)
                                }
                                true
                            }
                        }
                    }
                    false
                } ?: true
                running.removeAll { it.isCompleted }
                if (timedOut) break
            }
        } finally {
            // 够数收口：取消在飞的慢源，把闸门让给下一步拉列表
            if (out.size >= warmupCount) {
                for (d in running) d.cancel()
            }
        }
        out
    }

    /**
     * 按分类取列表（多源聚合，**映射→列表全流水线**）。
     *
     * 优化链：
     *  1. 分类表双层缓存（内存→磁盘）+ 按源合并在途请求
     *  2. [resolveTypeIds] 层间流水线 + **命中即投递**：首个映射到位立即开始拉
     *     列表，后续映射到位后增量并入任务队列——首屏不等 warmup 凑齐
     *  3. 后台继续补齐其余 tier 的映射（供翻页与下次进入使用）
     */
    suspend fun fetchByCategory(
        sources: List<CmsSource>,
        categoryName: String,
        page: Int,
        /** 首批（够填满首屏）就绪立即回调，用于「先给内容、再补完整」 */
        onFirstBatch: ((List<VideoItem>) -> Unit)? = null,
        /** 本次聚合的起点（ms）。默认现在，供调用方把解析阶段也算进预算 */
        startedAtMs: Long = Clock.nowMs(),
    ): FetchResult {
        // NFR-003：页面级总预算从**用户发起**那刻算，包含分类表解析阶段。
        val clock = PageClock(startedAtMs)

        // 【为什么映射要「命中即投递」而不是攒够 3 个再拉列表】
        //
        // 真机实测（蜂窝 + VPN 环境）：TYPE_RESOLVE_BUDGET_MS=3s 内 tier1 的
        // hhzy 1.4s 命中后，tier2 才刚起步、跑不完——「攒够再拉」的形态下首屏
        // 白等整个预算窗口（firstBatch 8s+），且到点收口时手里其实只有 1 个映射，
        // 多源与速度两头都没占到。而首屏三列网格 8 条就填满，一个源单次就返回
        // 20 条——首屏根本不需要等第二个映射。
        //
        // 改成流水线后：首屏关键路径 = 首个映射（~1.4s）+ 首个子分类列表
        // （~1.5s）≈ 3s；warmup=3 的多源混流由后续映射增量并入达成（最终结果
        // 仍是多源，海报不退回单源依赖）。
        // 跨协程共享：resolver 的 onMapped 回调投递任务，主循环消费。
        // 原实现用 ConcurrentLinkedQueue + AtomicBoolean；跨平台层改用
        // 普通 ArrayDeque/Boolean + [progressLock]（锁内只做 O(1) 操作，不跨挂起点）。
        val queue = ArrayDeque<SubTypeTask>()
        var resolveDone = false
        var everMapped = false
        val progressLock = PlatformLock()

        // 其余 tier **真·后台**补齐映射，写回缓存供翻页复用。
        // 注意这里不能用 coroutineScope：它会等子协程结束，把补齐变成前置串行步骤。
        //
        // 【必须延迟启动】补齐的 warmup=999 会经层间流水线把**全部 tier 的
        // 分类表请求**都放出去（29 个源）。全局闸门（ConcurrencyGate）先到先得，
        // t=0 就启动会把首屏列表请求全部挤到闸门后排queue——真机实测首屏
        // 26s 一无所获、且无任何网络失败日志（请求根本没发出）。延迟 3s =
        // 首屏列表请求已占坑后再补齐，两者不再抢闸门。
        bgScope.launch {
            kotlinx.coroutines.delay(3_000)
            runCatching { resolveTypeIds(sources, categoryName, 999) }
        }

        return coroutineScope {
            val resolver = async {
                try {
                    resolveTypeIds(
                        sources, categoryName, 3,
                        deadlineMs = startedAtMs + PageBudget.TYPE_RESOLVE_BUDGET_MS,
                        // 【无进展兜底】out 为空时 resolveTypeIds 不受 deadlineMs
                        // 约束（宁慢勿错），全挂情形会等满每个源的超时。给一条
                        // 10s 的硬底（一轮分类表超时的量级），保证「未找到分类」
                        // 的错误页在页面预算（15s）内出现，而不是 join 死等。
                        noProgressDeadlineMs = startedAtMs + 10_000,
                    ) { m ->
                        progressLock.withLock {
                            everMapped = true
                            for (id in m.subTypeIds) queue.add(SubTypeTask(m.source, id))
                        }
                    }
                } finally {
                    progressLock.withLock { resolveDone = true }
                }
            }

            val merged = ArrayList<VideoItem>()
            val seen = HashSet<String>()
            var pageCount = 1
            var srcName = ""
            var firstEmitted = false
            val gateLimit = ConcurrencyGate.OUTBOUND_LIMIT
            val running = ArrayList<Deferred<FetchResult>>()

            try {
                while (true) {
                    // 补满窗口：已上屏则回落 2，把闸门让给用户下一步操作。
                    // 剩余量 = 还在排队的（queue）+ 在飞的（running），
                    // 与旧 fetchFromMapped 的 tasks.size - nextToStart 同口径。
                    val pendingCount = progressLock.withLock { queue.size }
                    val window = ListBatch.windowFor(firstEmitted, gateLimit, pendingCount + running.size)
                    while (running.size < window) {
                        val t = progressLock.withLock { queue.removeFirstOrNull() } ?: break
                        running += async {
                            val r = fetchList(t.source, t.typeId, page)
                            // FR-107：分类列表结果同样记健康分。
                            // 死源在分类路径上的失败对健康表可见，才会被排到后面。
                            noteOutcome(t.source.key, r.outcome.toHealthSignal())
                            r
                        }
                    }

                    if (running.isEmpty()) {
                        // 映射解析已全部结束且没有任务：判「未找到分类」收尾
                        if (progressLock.withLock { resolveDone }) break
                        // 首个映射还在路上：等它（页面预算兜底，不无限等）
                        if (clock.remaining(Clock.nowMs()) <= 0) break
                        kotlinx.coroutines.delay(25)
                        continue
                    }

                    // 【页面预算兜底】NFR-003 要求页面级总请求 ≤ 15s。
                    // 到点后只要手里已有内容就收口，不再等剩余慢源。
                    val left = clock.remaining(Clock.nowMs())
                    if (left <= 0) break

                    // 等**最早返回的那个**，而不是等整批；最多等到预算截止。
                    val r = withTimeoutOrNull(left) {
                        select<FetchResult> { running.forEach { d -> d.onAwait { it } } }
                    }
                    running.removeAll { it.isCompleted }
                    if (r == null) break

                    if (r.hasData) {
                        if (srcName.isEmpty()) srcName = r.sourceName
                        if (r.pageCount > pageCount) pageCount = r.pageCount
                        for (it in r.items) {
                            // FR-304：与搜索路径用同一折叠键实现
                            val k = TitleKey.ofRaw(it.vod_name, it.vod_year)
                            if (seen.contains(k)) continue
                            seen.add(k)
                            merged += it
                        }
                    }

                    // 【首批即上屏】每落地一个结果就判一次，而不是等到批末。
                    if (onFirstBatch != null &&
                        ListBatch.shouldEmitFirstBatch(merged.size, firstEmitted)
                    ) {
                        firstEmitted = true
                        onFirstBatch(merged.toList())
                    }

                    // 够 30 条且映射已投完（队列空）就收口；映射还可能来就再等等
                    val drained = progressLock.withLock { queue.isEmpty() }
                    if (ListBatch.shouldStop(merged.size, progressLock.withLock { queue.size }) &&
                        (progressLock.withLock { resolveDone } || drained)
                    ) break
                }
            } finally {
                // 提前收口：取消在飞任务，把闸门让给用户下一步操作；
                // resolver 不取消——让它跑完把剩余映射写进缓存（翻页/下次进入用）
                for (d in running) d.cancel()
            }
            // 等 resolver 结束再返回（外层 coroutineScope 本会等 async 子协程，
            // 显式 join 让意图可读；其预算 3s 远小于页面预算，不会拖长尾）
            resolver.join()

            // 全部拉完仍未达首批门槛（该分类本身就少内容）：有结果就补上屏，
            // 否则用户会对着空列表等一个永远不会到来的"首批"。
            if (onFirstBatch != null && !firstEmitted && merged.isNotEmpty()) {
                firstEmitted = true
                onFirstBatch(merged.toList())
            }

            if (merged.isEmpty() && !progressLock.withLock { everMapped }) {
                // 一个映射都没解析到（分类表全挂）：真失败
                return@coroutineScope FetchResult(
                    outcome = RequestOutcome.RequestFailed,
                    page = page,
                    err = "未找到「$categoryName」分类",
                )
            }

            // FR-108：**空结果不是失败**。把"该分类确实没片"与"请求全失败"分开。
            val outcome =
                if (merged.isNotEmpty()) RequestOutcome.SuccessWithData else RequestOutcome.SuccessNoData
            FetchResult(
                outcome = outcome, items = merged, page = page, pageCount = pageCount,
                total = merged.size, sourceName = srcName,
            )
        }
    }

    /** 一次子分类拉取任务（源 + 分类 id） */
    private data class SubTypeTask(val source: CmsSource, val typeId: Int)

    // ---------------- 跨源候选（FR-417 / 自动换线） ----------------

    data class AltCandidate(
        val sourceKey: String,
        val vodId: Long,
        val vodName: String,
    )

    /**
     * 跨源候选：其他源搜同名片，返回 (source, vodId, 名称) 列表。
     *
     * 用于自动换线：当前源分片不可达时，找别的源的同名资源。
     * 按源速排序，跳过当前源本身。
     */
    suspend fun findAltSources(
        sources: List<CmsSource>,
        currentSourceKey: String,
        title: String,
    ): List<AltCandidate> {
        val norm = title.replace(Regex("[（(].*?[)）]"), "").trim()
        if (norm.isEmpty()) return emptyList()
        // 【进程级缓存】详情页预取与播放链兜底会先后用同一片名调本函数——
        // 每次都是 12+ 源并发搜索（2-5 秒）。tv189 免登录片全部走"跨源找全片"
        // 路径（v1.2.0 FR-610），播放链每次命中缓存才能把跨源代价压到只剩
        // "试候选"一段。搜索结果短时稳定，进程内存缓存即可（不落盘）。
        altSearchLock.withLock { altSearchCache[norm] }?.let { return it }
        // 【在途合并】详情页预取与播放链兜底几乎同时调用（用户点正片时预取
        // 往往还在搜）——两路并行各搜 12+ 源白费 2-5 秒还互相挤带宽。后到者
        // 直接 await 首搜结果。
        altSearchLock.withLock { inflightAlt[norm] }?.let { return it.await() }
        return coroutineScope {
            val task = async {
            val out = ArrayList<AltCandidate>()
            val seenSrc = HashSet<String>()
            val enabled = sources
            .filter {
                isSourceEnabled(it) && it.key != currentSourceKey &&
                    it.caps.search && it.caps.detail
            }
            .sortedWith(compareBy<CmsSource> { it.tier }.thenBy { it.speed })

        val tasks = enabled.map { s ->
            // 后台档：候选源搜索是「多给几个线选项」，晚到几十毫秒无感，
            // 不该跟用户正等着的详情/搜索抢空位（实测能抢走整整 3 秒，见 docs/04 §16.34）
            async(NetPriority.Background) {
                val r = try {
                    search(s, norm)
                } catch (e: Exception) {
                    return@async
                }
                if (!r.hasData) return@async

                // 找名称最接近的条目（完全相等 > 前缀包含）
                var best: VideoItem? = null
                for (it in r.items) {
                    val n = it.vod_name.replace(Regex("[（(].*?[)）]"), "").trim()
                    if (n == norm) {
                        best = it
                        break
                    }
                    if (best == null && (n.startsWith(norm) || norm.startsWith(n))) best = it
                }
                if (best != null && !seenSrc.contains(s.key)) {
                    seenSrc.add(s.key)
                    out += AltCandidate(s.key, best.vod_id, best.vod_name)
                }
            }
        }
        // 【到点就用已有候选，不等最慢的源】
        //
        // 原实现 `tasks.awaitAll()` 等**全部**源跑完。真机探针抓到
        // `alt-sources-loaded elapsed=14824 / 14961 / 14310 / 21741 / 19363`，
        // 而候选数只有 n=4~15 —— 早在第一两轮就已够用，后面十几秒全在等慢源。
        //
        // 超时取消后 `out` 里已经收集到的候选照常返回：这条路径是"多给几个
        // 换线选项"，少几个候选远好过让用户对着转圈等 20 秒。
        withTimeoutOrNull(PageBudget.ALT_SOURCES_BUDGET_MS) { tasks.awaitAll() }
            if (out.isNotEmpty()) {
                altSearchLock.withLock { altSearchCache[norm] = out.toList() }
            }
            out
            }
            altSearchLock.withLock { inflightAlt[norm] = task }
            val r = runCatching { task.await() }.getOrDefault(emptyList())
            altSearchLock.withLock { inflightAlt.remove(norm) }
            r
        }
    }

    /**
     * 跨源换线：取候选源详情，返回指定集数的播放地址（集数越界回退第 1 集）。
     *
     * token 型源在此一并换取最终地址，调用方不再关心协议差异。
     */
    suspend fun fetchAltPlayUrl(source: CmsSource, vodId: Long, epIndex: Int = 0): String {
        val it = fetchDetail(source, vodId) ?: return ""
        val playUrl = it.vod_play_url ?: return ""
        if (playUrl.isEmpty()) return ""

        val fromNames = (it.vod_play_from ?: "").split("$$$")
        val groups = playUrl.split("$$$")
        var token = ""
        var lineFrom = ""
        for (g in groups.indices) {
            val eps = groups[g].split("#")
            val idx = if (epIndex > 0 && epIndex < eps.size) epIndex else 0
            val part = eps.getOrElse(idx) { "" }
            val cut = part.indexOf('$')
            if (cut > 0) {
                token = part.substring(cut + 1)
                lineFrom = fromNames.getOrElse(g) { "" }
                break
            }
        }
        if (token.isEmpty()) return ""
        return resolveSourcePlay(source, vodId, token, lineFrom).url
    }

    /** 供页面取源清单 */
    fun allSources(): List<CmsSource> = CmsSources.ALL
}

/** 分类 → type_id 映射项（含子分类） */
data class TypeIdMap(
    val source: CmsSource,
    /** 顶级分类 id（保留用于日志/调试） */
    val typeId: Int,
    /** 顶级 + 全部子分类 id，逐个拉取后合并 */
    val subTypeIds: List<Int>,
)

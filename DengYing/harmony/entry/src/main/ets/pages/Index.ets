import dengying from 'libentry.so';
import { hilog } from '@kit.PerformanceAnalysisKit';

// hilog domain 只有 16 位（0x0000–0xFFFF）：0xA0000 会被静默丢弃，日志永不出现
const DOMAIN = 0x0000;
const TAG = 'DengYingUI';

interface VideoItem {
  id: number;
  name: string;
  pic: string;
  remarks: string;
  year: string;
  area: string;
}

interface TaskResult {
  tag: string;
  partial: boolean;
  elapsedMs: number;
  n: number;
  err?: string;
  tip?: string;
  cancelled?: boolean;
  items: VideoItem[];
}

interface SuggestItem {
  title: string;
}

@Entry
@Component
struct Index {
  @State categories: string[] = [];
  @State activeCategory: string = '';
  @State items: VideoItem[] = [];
  @State statusText: string = '正在初始化…';
  @State loading: boolean = false;
  @State mode: string = 'home';
  @State keyword: string = '';
  @State suggestWords: string[] = [];
  @State historyWords: string[] = [];
  @State searchItems: VideoItem[] = [];
  @State searchTip: string = '';
  @State searching: boolean = false;
  private expectTag: string = '';
  private pollTimer: number = -1;
  private suggestTimer: number = -1;

  aboutToAppear(): void {
    const catsJson = dengying.homeCategories();
    try {
      this.categories = JSON.parse(catsJson) as string[];
    } catch (err) {
      hilog.error(DOMAIN, TAG, 'parse categories failed: %{public}s', String(err));
    }
    if (this.categories.length > 0) {
      this.activeCategory = this.categories[0];
      this.startFetch();
    } else {
      this.statusText = 'categories empty (app not initialized?)';
    }
    this.loadHistory();
  }

  aboutToDisappear(): void {
    this.stopPoll();
    this.stopSuggestPoll();
  }

  private stopPoll(): void {
    if (this.pollTimer >= 0) {
      clearInterval(this.pollTimer);
      this.pollTimer = -1;
    }
  }

  private stopSuggestPoll(): void {
    if (this.suggestTimer >= 0) {
      clearInterval(this.suggestTimer);
      this.suggestTimer = -1;
    }
  }

  private loadHistory(): void {
    try {
      this.historyWords = JSON.parse(dengying.getHistory()) as string[];
    } catch (err) {
      hilog.error(DOMAIN, TAG, 'parse history failed: %{public}s', String(err));
    }
  }

  private startFetch(): void {
    this.stopPoll();
    this.loading = true;
    this.items = [];
    this.statusText = `拉取「${this.activeCategory}」中…`;
    const started = dengying.fetchCategory(this.activeCategory, 1);
    if (started !== 1) {
      this.loading = false;
      this.statusText = 'fetch start failed (任务被占用或未初始化)';
      return;
    }
    this.expectTag = `cat:${this.activeCategory}#1`;
    this.pollTimer = setInterval(() => {
      if (dengying.taskRunning() === 1) {
        return;
      }
      const json = dengying.taskResult();
      this.stopPoll();
      this.loading = false;
      this.applyResult(json);
    }, 300);
  }

  private applyResult(json: string | null): void {
    if (json === null || json.length === 0) {
      this.statusText = 'no result';
      return;
    }
    try {
      const parsed = JSON.parse(json) as TaskResult;
      if (parsed.tag !== this.expectTag) {
        return;
      }
      this.items = (parsed.items !== undefined) ? parsed.items : [];
      const elapsed = (parsed.elapsedMs / 1000).toFixed(1);
      if (parsed.err !== undefined) {
        this.statusText = `错误：${parsed.err}`;
      } else {
        this.statusText = `${parsed.tag}：${parsed.n} 条（${elapsed}s${parsed.partial ? '，首批' : ''}）`;
      }
    } catch (err) {
      hilog.error(DOMAIN, TAG, 'parse task result failed: %{public}s', String(err));
      this.statusText = 'result parse failed';
    }
  }

  // ---------------- 搜索（P6-7） ----------------

  private doSearch(raw: string): void {
    const kw = raw.trim();
    hilog.warn(0x0000, 'DengYingUI', 'doSearch enter kw=%{public}s', kw);
    if (kw.length === 0) {
      this.searchTip = '请输入片名';
      return;
    }
    this.mode = 'search';
    this.keyword = kw;
    this.searchTip = '正在搜索…';
    this.searching = true;
    this.stopPoll();
    const started = dengying.searchAll(kw);
    hilog.warn(0x0000, TAG, 'doSearch searchAll returned=%{public}d', started);
    if (started !== 1) {
      this.searching = false;
      this.searchTip = '搜索启动失败（已有任务在跑或未初始化）';
      return;
    }
    this.expectTag = `search:${kw}`;
    this.pollTimer = setInterval(() => {
      const json = dengying.taskResult();
      if (json !== null && json.length > 0) {
        this.applySearchResult(json);
      }
      if (dengying.taskRunning() === 0) {
        this.stopPoll();
        this.searching = false;
        this.loadHistory();
      }
    }, 300);
  }

  private applySearchResult(json: string): void {
    try {
      const parsed = JSON.parse(json) as TaskResult;
      if (parsed.tag !== this.expectTag) {
        return;
      }
      if (parsed.cancelled === true) {
        this.searchTip = (parsed.tip !== undefined) ? parsed.tip : '';
        return;
      }
      this.searchItems = (parsed.items !== undefined) ? parsed.items : [];
      if (parsed.err !== undefined) {
        this.searchTip = `错误：${parsed.err}`;
      } else if (parsed.tip !== undefined && parsed.tip.length > 0) {
        this.searchTip = parsed.tip;
      } else if (!parsed.partial) {
        this.searchTip = `共 ${parsed.n} 条 · ${(parsed.elapsedMs / 1000).toFixed(1)}s`;
      } else {
        this.searchTip = '';
      }
    } catch (err) {
      hilog.error(DOMAIN, TAG, 'parse search result failed: %{public}s', String(err));
    }
  }

  private onKeywordChange(value: string): void {
    this.keyword = value;
    dengying.startSuggest(value);
    if (value.trim().length === 0) {
      this.suggestWords = [];
    }
    if (this.mode === 'search' && this.suggestTimer < 0) {
      this.suggestTimer = setInterval(() => {
        if (this.searching || this.keyword.trim().length === 0) {
          return;
        }
        try {
          const arr = JSON.parse(dengying.suggestResult()) as SuggestItem[];
          const words: string[] = [];
          for (let i = 0; i < arr.length; i++) {
            words.push(arr[i].title);
          }
          this.suggestWords = words;
        } catch (err) {
          // 联想是尽力而为：解析失败静默（对齐 Android）
        }
      }, 400);
    }
  }

  private backHome(): void {
    this.stopPoll();
    this.stopSuggestPoll();
    this.mode = 'home';
    this.searching = false;
    this.suggestWords = [];
    this.keyword = '';
    this.loadHistory();
  }

  private confirmClearHistory(): void {
    this.getUIContext().showAlertDialog({
      title: '清空搜索历史',
      message: '确定要清空全部搜索历史吗？',
      primaryButton: {
        value: '取消',
        action: () => {
        },
      },
      secondaryButton: {
        value: '清空',
        action: () => {
          if (dengying.clearHistory() === 1) {
            this.loadHistory();
          }
        },
      },
    });
  }

  @Builder
  ItemRow(item: VideoItem) {
    Row({ space: 10 }) {
      Image(item.pic)
        .width(72)
        .height(100)
        .borderRadius(6)
        .objectFit(ImageFit.Cover)
        .backgroundColor('#EEEEEE')
      Column({ space: 4 }) {
        Text(item.name)
          .fontSize(16)
          .fontWeight(FontWeight.Medium)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(`${item.year} · ${item.area}`)
          .fontSize(12)
          .fontColor('#999999')
        Text(item.remarks)
          .fontSize(12)
          .fontColor('#666666')
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)
    }
    .width('100%')
    .alignItems(VerticalAlign.Top)
  }

  build() {
    Column() {
      // 搜索栏（两态共用）
      Row({ space: 8 }) {
        if (this.mode === 'search') {
          Text('←')
            .fontSize(22)
            .fontColor('#333333')
            .padding({ left: 4, right: 4 })
            .onClick(() => this.backHome())
        }
        TextInput({ placeholder: '搜索片名', text: this.keyword })
          .layoutWeight(1)
          .height(38)
          .onFocus(() => {
            if (this.mode === 'home') {
              this.mode = 'search';
              this.keyword = '';
              this.searchItems = [];
              this.searchTip = '';
            }
          })
          .onChange((value: string) => this.onKeywordChange(value))
          .onSubmit(() => this.doSearch(this.keyword))
        Button(this.searching ? '取消' : '搜索')
          .height(38)
          .onClick(() => {
            if (this.searching) {
              hilog.warn(DOMAIN, TAG, 'cancel tapped');
              dengying.cancelSearch();
            } else {
              this.doSearch(this.keyword);
            }
          })
      }
      .width('100%')
      .padding({ left: 12, right: 12, top: 12 })

      if (this.mode === 'home') {
        Text('P6-7 :shared 数据门面（分类 + 搜索）')
          .fontSize(13)
          .fontColor('#888888')
          .margin({ top: 6, bottom: 6 })

        Row() {
          ForEach(this.categories, (cat: string) => {
            Text(cat)
              .fontSize(15)
              .fontColor(cat === this.activeCategory ? '#FFFFFF' : '#333333')
              .backgroundColor(cat === this.activeCategory ? '#3366FF' : '#F0F0F0')
              .borderRadius(14)
              .padding({ left: 12, right: 12, top: 5, bottom: 5 })
              .margin({ left: 4, right: 4 })
              .onClick(() => {
                if (cat !== this.activeCategory) {
                  this.activeCategory = cat;
                  this.startFetch();
                }
              })
          }, (cat: string) => cat)
        }

        Text(this.statusText)
          .fontSize(13)
          .fontColor(this.items.length > 0 ? '#2E7D32' : '#B71C1C')
          .margin({ top: 8, bottom: 8 })

        if (this.loading) {
          LoadingProgress().width(40).height(40)
        }

        List({ space: 8 }) {
          ForEach(this.items, (item: VideoItem) => {
            ListItem() {
              this.ItemRow(item)
            }
          }, (item: VideoItem) => `${item.id}`)
        }
        .layoutWeight(1)
        .width('100%')
        .padding({ left: 12, right: 12 })
      } else {
        // 搜索态：历史 / 联想 / 结果
        if (!this.searching && this.keyword.trim().length === 0 && this.historyWords.length > 0) {
          Row() {
            Text('搜索历史')
              .fontSize(13)
              .fontColor('#888888')
              .margin({ right: 8 })
            Text('清空')
              .fontSize(13)
              .fontColor('#3366FF')
              .onClick(() => this.confirmClearHistory())
          }
          .width('100%')
          .padding({ left: 12, top: 8 })
          .justifyContent(FlexAlign.Start)

          Flex({ wrap: FlexWrap.Wrap }) {
            ForEach(this.historyWords, (kw: string) => {
              Row({ space: 2 }) {
                Text(kw)
                  .fontSize(14)
                  .onClick(() => this.doSearch(kw))
                Text('×')
                  .fontSize(14)
                  .fontColor('#999999')
                  .onClick(() => {
                    if (dengying.removeHistory(kw) === 1) {
                      this.loadHistory();
                    }
                  })
              }
              .backgroundColor('#F0F0F0')
              .borderRadius(14)
              .padding({ left: 12, right: 8, top: 5, bottom: 5 })
              .margin({ left: 4, right: 4, top: 4, bottom: 4 })
            }, (kw: string) => kw)
          }
          .width('100%')
          .padding({ left: 8, right: 12 })
        }

        if (!this.searching && this.suggestWords.length > 0) {
          Flex({ wrap: FlexWrap.Wrap }) {
            ForEach(this.suggestWords, (word: string) => {
              Text(word)
                .fontSize(14)
                .fontColor('#3366FF')
                .backgroundColor('#EBF1FF')
                .borderRadius(14)
                .padding({ left: 12, right: 12, top: 5, bottom: 5 })
                .margin({ left: 4, right: 4, top: 4, bottom: 4 })
                .onClick(() => this.doSearch(word))
            }, (word: string) => word)
          }
          .width('100%')
          .padding({ left: 8, right: 12, top: 6 })
        }

        if (this.searchTip.length > 0) {
          Text(this.searchTip)
            .fontSize(13)
            .fontColor(this.searchItems.length > 0 ? '#2E7D32' : '#B71C1C')
            .margin({ top: 8, bottom: 8 })
            .padding({ left: 12, right: 12 })
        }

        if (this.searching) {
          LoadingProgress().width(40).height(40)
        }

        List({ space: 8 }) {
          ForEach(this.searchItems, (item: VideoItem) => {
            ListItem() {
              this.ItemRow(item)
            }
          }, (item: VideoItem) => `s-${item.id}`)
        }
        .layoutWeight(1)
        .width('100%')
        .padding({ left: 12, right: 12 })
      }
    }
    .width('100%')
    .height('100%')
  }
}

#ifndef KONAN_LIBDENGYUI_H
#define KONAN_LIBDENGYUI_H
#ifdef __cplusplus
extern "C" {
#endif
#ifdef __cplusplus
typedef bool            libdengyui_KBoolean;
#else
typedef _Bool           libdengyui_KBoolean;
#endif
typedef unsigned short     libdengyui_KChar;
typedef signed char        libdengyui_KByte;
typedef short              libdengyui_KShort;
typedef int                libdengyui_KInt;
typedef long long          libdengyui_KLong;
typedef unsigned char      libdengyui_KUByte;
typedef unsigned short     libdengyui_KUShort;
typedef unsigned int       libdengyui_KUInt;
typedef unsigned long long libdengyui_KULong;
typedef float              libdengyui_KFloat;
typedef double             libdengyui_KDouble;
typedef float __attribute__ ((__vector_size__ (16))) libdengyui_KVector128;
typedef void*              libdengyui_KNativePtr;
struct libdengyui_KType;
typedef struct libdengyui_KType libdengyui_KType;

typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlin_Byte;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlin_Short;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlin_Int;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlin_Long;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlin_Float;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlin_Double;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlin_Char;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlin_Boolean;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlin_Unit;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlin_UByte;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlin_UShort;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlin_UInt;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlin_ULong;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_app_OhosApp;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_platform_FileKeyValueStore;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_cache_PageCache;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_SourceRepository;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_store_Store;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlin_collections_List;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_OutcomeFacts;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_RequestOutcome;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_HealthSignal;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_SourceCaps;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_CmsSource;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_SourceProtocol;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_DiscoverKind;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_DiscoverKind_DoubanAnime;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_DiscoverKind_IqiyiSuggest;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_DiscoverItem;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlin_Any;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_DiscoverFeed;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_DiscoverQuery;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_DoubanRankingTag;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlin_Pair;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_LogTags;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_VideoItem;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_Episode;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_PlayLine;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_CmsResponse;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_CmsClass;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_FavoriteItem;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_HistoryItem;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_RequestOutcome_SuccessWithData;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_RequestOutcome_SuccessNoData;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_RequestOutcome_ParseFailed;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_RequestOutcome_RequestFailed;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_HealthSignal_Ok;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_HealthSignal_SuccessNoData;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_HealthSignal_Failed;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_OutcomeMessages;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_SourceSecrets;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_SourceProtocol_Cms;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_SourceProtocol_Api2233;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_SourceProtocol_Mino;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_SourceProtocol_Wencai;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_SourceProtocol_Duboku;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_SourceProtocol_Html;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_SourceProtocol_Juzhi;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_SourceProtocol_Guapi;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_SourceProtocol_Shanzha;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_SourceProtocol_Tv189;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_FetchResult;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_PlayResolveResult;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_WebSearchRule;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_WebIdMode;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_WebIdMode_TrailingNumber;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_WebIdMode_SlugHash;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_WebItemRule;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_WebLineRule;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_WebEpisodeRule;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_WebPlayRule;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlin_collections_Map;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_WebTokenPlayRule;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_WebTokenPlayAlg;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_WebTokenPlayAlg_BttwoV1;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_WebRule;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_DirectSource;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_core_CmsSources;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_cache_MediaCache;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_platform_KeyValueStore;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_cache_MediaCacheHost;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_json_JsonX;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlinx_serialization_json_Json;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlinx_serialization_json_JsonElement;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlinx_serialization_json_JsonArray;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlinx_serialization_json_JsonObject;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlin_Array;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_json_StoreEnvelope;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_DecodeResult;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_Kind;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_Kind_Ok;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_Kind_Corrupt;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_Kind_NeedsUpgrade;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_net_HlsUtil;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_net_HlsUtil_SharePageFacts;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolvedPlay;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolveOutcome;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_net_HlsUtil_ReachResult;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_net_HttpClient;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpResult;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpBinaryResult;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlin_ByteArray;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_net_RawResponse;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_net_NetPriority;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_net_NetPriority_Key;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_net_HttpTransport;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_SourceOverrideCodec;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_TokenKind;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_Scan;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_TokenKind_Bba;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_TokenKind_Nby;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_TokenKind_Url;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_TokenKind_Unknown;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_BttwoSign;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_CmsAdapter;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_CmsParser;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_DubokuAdapter;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_DubokuParser;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_GuapiAdapter;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_GuapiAdapter_Session;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_GuapiParser;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiAdapter;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser_PlayParams;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiRoot;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_MinoAdapter;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_MinoParser;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_Parallel;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_ShanzhaAdapter;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_ShanzhaParser;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_Tv189Adapter;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_Tv189Parser;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_WebAdapter;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_WebParser;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_WencaiAdapter;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_adapters_WencaiParser;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_crypto_Aes;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_crypto_Digest;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_crypto_JavaRandom;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_crypto_JavaRandom_Companion;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Field;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Writer;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_crypto_Rsa;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_discover_DiscoverAdapter;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_discover_DiscoverParser;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlin_coroutines_SuspendFunction0;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlin_coroutines_SuspendFunction1;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_SourceRepository_ClassCacheEntry;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_SourceRepository_AltCandidate;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_data_source_TypeIdMap;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_store_StoreGate_State;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_page_ConcurrencyGate;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_page_ConcurrencyGate_Companion;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_page_ListBatch;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_page_PageBudget;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_page_PageBudget_Outcome;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_page_PageBudget_Outcome_Complete;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_page_PageBudget_Outcome_Expired;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_page_PageBudget_Outcome_Cancelled;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_page_PageClock;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_page_PageCacheRules;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_page_PageCacheRules_Entry;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_CastUtil;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_CastUtil_BlockReason;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_CastUtil_FailureKind;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_CastUtil_Stage;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_CastUtil_Castability;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_CastUtil_CastabilityKind;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_CastUtil_CastabilityKind_Castable;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_CastUtil_CastabilityKind_NotCastable;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_CastUtil_CastabilityKind_Trial;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_CastUtil_BlockReason_None;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_CastUtil_BlockReason_NeedsHeaders;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_CastUtil_BlockReason_AddressNotReady;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_CastUtil_FailureKind_DeviceUnreachable;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_CastUtil_FailureKind_RemotePullFail;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_CastUtil_FailureKind_AddressGone;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_CastUtil_Stage_Precheck;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_CastUtil_Stage_Link;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_CastUtil_Stage_Handoff;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_CastUtil_Stage_Remote;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_GestureRules;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_GestureRules_Action;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_GestureRules_Action_Undecided;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_GestureRules_Action_Seek;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_GestureRules_Action_Brightness;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_GestureRules_Action_Volume;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_GestureRules_Action_Discard;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_HlsVariants;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_HlsVariants_Variant;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Decision;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Verdict;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Verdict_Play;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Verdict_StayNoLines;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Verdict_StayEpMissing;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Apply;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Load;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_playback_SeekPreview;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_search_DiscoverRules;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_search_SearchNotice;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_search_SearchNotice_EpisodeState;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_search_SearchNotice_SearchableSource;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_search_SearchNotice_EpisodeKind;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_search_SearchNotice_EpisodeKind_Ok;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_search_SearchNotice_EpisodeKind_NoLines;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_search_SearchSession;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_search_TitleKey;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Health;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Outcome;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_State;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Outcome_Ok;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Outcome_SuccessNoData;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Outcome_Failed;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_State_Active;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_State_Suspicious;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Override;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Baseline;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_store_FavoritePrune;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_store_FavoritePrune_Entry;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_store_LedgerPrune;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_store_LedgerPrune_Entry;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_store_StoreGate;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlinx_coroutines_CoroutineScope;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_store_StoreGate_Verdict;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_store_StoreGate_State_Pending;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_store_StoreGate_State_Ready;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_store_StoreGate_State_Failed;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_store_StoreGate_Verdict_Proceed;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_store_StoreGate_Verdict_Wait;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_store_StoreGate_Verdict_Reject;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec_Spec;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_ui_TouchTarget;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Target;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Verdict;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_ui_UrlGuard;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Result;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Verdict;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Verdict_Ok;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Verdict_OkOffList;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Verdict_RejectScheme;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Verdict_RejectPrivate;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Verdict_RejectEmpty;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlinx_coroutines_CoroutineDispatcher;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_platform_HashAlg;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_platform_HashAlg_Md5;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_platform_HashAlg_Sha1;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_platform_HashAlg_Sha256;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlin_collections_Set;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_platform_AesCipher;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_platform_Clock;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_platform_Hash;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_platform_Log;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlin_Throwable;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_platform_PlatformLock;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_platform_RsaCipher;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_platform_TimeFormat;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_platform_UrlCodec;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_playback_PlaybackSession;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlinx_coroutines_flow_StateFlow;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_playback_PlaybackSession_State;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_player_PlayerEngineHost;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_playback_PlaybackSession_Status;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_playback_PlaybackSession_Status_Idle;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_playback_PlaybackSession_Status_Resolving;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_playback_PlaybackSession_Status_Buffering;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_playback_PlaybackSession_Status_Playing;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_playback_PlaybackSession_Status_Paused;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_playback_PlaybackSession_Status_Error;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_player_PlaybackErrorKind;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_player_PlaybackErrorKind_Network;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_player_PlaybackErrorKind_SourceGone;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_player_PlaybackErrorKind_Decode;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_player_PlaybackErrorKind_Unknown;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_player_PlayerEngine;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_player_PlayerListener;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_player_PlaybackProbe;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_UiDeps;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_viewmodel_LruCache;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_UiDeps_Companion;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_UiDepsHolder;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_components_PosterDecoder;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_androidx_compose_ui_graphics_ImageBitmap;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_components_PosterFetch;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_components_DyIcons;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_components_DyIcon;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_navigation_Routes;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_platform_FullscreenState;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_platform_BrightnessController;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_platform_VolumeController;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_theme_Palette;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_viewmodel_AltItem;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_viewmodel_FavoritesViewModel;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_viewmodel_FavoritesViewModel_UiState;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel_UiState;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_UiState;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_UiState;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_Row;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_com_dengying_player_ui_viewmodel_SuggestDebouncer;
typedef struct {
  libdengyui_KNativePtr pinned;
} libdengyui_kref_kotlinx_coroutines_Job;

extern libdengyui_KInt dengy_app_init(const char* dataDir);
extern libdengyui_KInt dengy_cancel_search();
extern libdengyui_KInt dengy_clear_history();
extern libdengyui_KInt dengy_fetch_category(const char* category, libdengyui_KInt page);
extern const char* dengy_get_history();
extern const char* dengy_home_categories();
extern libdengyui_KInt dengy_refresh_history();
extern libdengyui_KInt dengy_remove_history(const char* keyword);
extern libdengyui_KInt dengy_search_all(const char* keyword);
extern libdengyui_KInt dengy_start_suggest(const char* keyword);
extern const char* dengy_suggest_result();
extern const char* dengy_task_result();
extern libdengyui_KInt dengy_task_running();
extern const char* dengy_http_test_result();
extern libdengyui_KInt dengy_http_test_running();
extern libdengyui_KInt dengy_start_http_test(const char* url);
extern void dengy_http_complete(libdengyui_KLong id, libdengyui_KInt status, libdengyui_KInt binary, void* payloadBase64, libdengyui_KInt payloadLen);
extern void* MainArkUIViewController(void* env);
extern void InitJsRenderNodeContext(void* env, void* nodeConstructor, void* statusModifyConstructor, libdengyui_KDouble ratio, libdengyui_KBoolean fixed);
extern void androidx_compose_ui_arkui_ArkUIViewController_aboutToAppear(void* controllerRef);
extern void androidx_compose_ui_arkui_ArkUIViewController_aboutToDisappear(void* controllerRef);
extern void androidx_compose_ui_arkui_ArkUIViewController_cancelSyncRefresh(void* controllerRef, libdengyui_KInt refreshId);
extern libdengyui_KBoolean androidx_compose_ui_arkui_ArkUIViewController_dispatchComposeKeyEvent(void* controllerRef, libdengyui_KLong keyCode, libdengyui_KInt nativeAction, libdengyui_KLong modifierKeyStates);
extern libdengyui_KBoolean androidx_compose_ui_arkui_ArkUIViewController_dispatchComposeMouseEvent(void* controllerRef, void* nativeMouseEvent);
extern libdengyui_KBoolean androidx_compose_ui_arkui_ArkUIViewController_dispatchComposePinchEvent(void* controllerRef, void* nativePinchEvent);
extern void androidx_compose_ui_arkui_ArkUIViewController_dispatchHoverEvent(void* controllerRef);
extern void androidx_compose_ui_arkui_ArkUIViewController_dispatchMouseEvent(void* controllerRef);
extern libdengyui_KBoolean androidx_compose_ui_arkui_ArkUIViewController_dispatchTouchEvent(void* controllerRef, void* nativeTouchEvent, libdengyui_KBoolean ignoreInteropView);
extern void AndroidxComposeUiArkuiArkUiViewControllerDraw(void* controllerRef, void* canvas);
extern libdengyui_KInt androidx_compose_ui_arkui_ArkUIViewController_findNodeIdAt(void* controllerRef, libdengyui_KFloat x, libdengyui_KFloat y);
extern const char* androidx_compose_ui_arkui_ArkUIViewController_getId(void* controllerRef);
extern void* AndroidxComposeUiArkuiArkUiViewControllerGetJsNode(void* controllerRef);
extern libdengyui_KInt androidx_compose_ui_arkui_ArkUIViewController_getRendererTypeId();
extern void* androidx_compose_ui_arkui_ArkUIViewController_getXComponentRender(void* controllerRef);
extern void androidx_compose_ui_arkui_ArkUIViewController_initFusionRendererNode(void* controllerRef, libdengyui_KBoolean enableCApi, void* rootContent, void* frameMgr);
extern void androidx_compose_ui_arkui_ArkUIViewController_initFusionRendererNodePreComposed(void* controllerRef, libdengyui_KBoolean enableCApi, void* rootContent, void* frameMgr, libdengyui_KBoolean isPreCompose);
extern void androidx_compose_ui_arkui_ArkUIViewController_keyboardWillHide(void* controllerRef);
extern void androidx_compose_ui_arkui_ArkUIViewController_keyboardWillShow(void* controllerRef, libdengyui_KFloat keyboardHeight);
extern libdengyui_KBoolean androidx_compose_ui_arkui_ArkUIViewController_onBackPress(void* controllerRef);
extern void androidx_compose_ui_arkui_ArkUIViewController_onConfigurationUpdate(void* controllerRef);
extern void androidx_compose_ui_arkui_ArkUIViewController_onFinalize(void* controllerRef);
extern void androidx_compose_ui_arkui_ArkUIViewController_onFocusEvent(void* controllerRef);
extern void androidx_compose_ui_arkui_ArkUIViewController_onFrame(void* controllerRef, libdengyui_KLong timestamp, libdengyui_KLong targetTimestamp);
extern void AndroidXComposeUIArkUIArkUIViewControllerOnIdle(void* controllerRef, libdengyui_KLong timeLeft);
extern void androidx_compose_ui_arkui_ArkUIViewController_onKeyEvent(void* controllerRef);
extern void androidx_compose_ui_arkui_ArkUIViewController_onPageHide(void* controllerRef);
extern void androidx_compose_ui_arkui_ArkUIViewController_onPageShow(void* controllerRef);
extern void androidx_compose_ui_arkui_ArkUIViewController_onSurfaceChanged(void* controllerRef, libdengyui_KInt width, libdengyui_KInt height);
extern void androidx_compose_ui_arkui_ArkUIViewController_onSurfaceCreated(void* controllerRef, void* xcomponentPtr, libdengyui_KInt width, libdengyui_KInt height);
extern void androidx_compose_ui_arkui_ArkUIViewController_onSurfaceDestroyed(void* controllerRef);
extern void androidx_compose_ui_arkui_ArkUIViewController_onSurfaceHide(void* controllerRef);
extern void androidx_compose_ui_arkui_ArkUIViewController_onSurfaceShow(void* controllerRef);
extern libdengyui_KInt androidx_compose_ui_arkui_ArkUIViewController_requestSyncRefresh(void* controllerRef);
extern const char* androidx_compose_ui_arkui_ArkUIViewController_sendMessage(void* controllerRef, const char* type, const char* message);
extern void androidx_compose_ui_arkui_ArkUIViewController_setContext(void* controllerRef, void* context);
extern void androidx_compose_ui_arkui_ArkUIViewController_setEnv(void* controllerRef, void* env);
extern void androidx_compose_ui_arkui_ArkUIViewController_setFusionRendererRootNode(void* controllerRef, void* rootFrameNode, void* uiContext);
extern void androidx_compose_ui_arkui_ArkUIViewController_setId(void* controllerRef, const char* id);
extern void androidx_compose_ui_arkui_ArkUIViewController_setLocaleAndStringProvider(void* controllerRef, void* provider);
extern void androidx_compose_ui_arkui_ArkUIViewController_setMessenger(void* controllerRef, void* messenger);
extern void androidx_compose_ui_arkui_ArkUIViewController_setRendererBackendId(void* controllerRef, libdengyui_KInt backendId);
extern void androidx_compose_ui_arkui_ArkUIViewController_setRootView(void* controllerRef, void* backRootView, void* foreRootView, void* touchableRootView);
extern void androidx_compose_ui_arkui_ArkUIViewController_setUIContext(void* controllerRef, void* uiContext);
extern void androidx_compose_ui_arkui_ArkUIViewController_setXComponentRender(void* controllerRef, void* render);
extern void androidx_compose_ui_platform_LowMemoryMonitor_notifyLowMemoryFromHost(libdengyui_KInt rawLevel);
extern libdengyui_KFloat androidx_compose_ui_arkui_ArkUIViewController_consumeOuterScroll(void* controllerRef, libdengyui_KFloat offsetVp, libdengyui_KInt sourceInt);
extern void androidx_compose_ui_arkui_ArkUIViewController_setParentScrollBridge(void* controllerRef, void* scroller);
extern void androidx_compose_ui_arkui_init(void* env, void* exports);

typedef struct {
  /* Service functions. */
  void (*DisposeStablePointer)(libdengyui_KNativePtr ptr);
  void (*DisposeString)(const char* string);
  libdengyui_KBoolean (*IsInstance)(libdengyui_KNativePtr ref, const libdengyui_KType* type);
  libdengyui_kref_kotlin_Byte (*createNullableByte)(libdengyui_KByte);
  libdengyui_KByte (*getNonNullValueOfByte)(libdengyui_kref_kotlin_Byte);
  libdengyui_kref_kotlin_Short (*createNullableShort)(libdengyui_KShort);
  libdengyui_KShort (*getNonNullValueOfShort)(libdengyui_kref_kotlin_Short);
  libdengyui_kref_kotlin_Int (*createNullableInt)(libdengyui_KInt);
  libdengyui_KInt (*getNonNullValueOfInt)(libdengyui_kref_kotlin_Int);
  libdengyui_kref_kotlin_Long (*createNullableLong)(libdengyui_KLong);
  libdengyui_KLong (*getNonNullValueOfLong)(libdengyui_kref_kotlin_Long);
  libdengyui_kref_kotlin_Float (*createNullableFloat)(libdengyui_KFloat);
  libdengyui_KFloat (*getNonNullValueOfFloat)(libdengyui_kref_kotlin_Float);
  libdengyui_kref_kotlin_Double (*createNullableDouble)(libdengyui_KDouble);
  libdengyui_KDouble (*getNonNullValueOfDouble)(libdengyui_kref_kotlin_Double);
  libdengyui_kref_kotlin_Char (*createNullableChar)(libdengyui_KChar);
  libdengyui_KChar (*getNonNullValueOfChar)(libdengyui_kref_kotlin_Char);
  libdengyui_kref_kotlin_Boolean (*createNullableBoolean)(libdengyui_KBoolean);
  libdengyui_KBoolean (*getNonNullValueOfBoolean)(libdengyui_kref_kotlin_Boolean);
  libdengyui_kref_kotlin_Unit (*createNullableUnit)(void);
  libdengyui_kref_kotlin_UByte (*createNullableUByte)(libdengyui_KUByte);
  libdengyui_KUByte (*getNonNullValueOfUByte)(libdengyui_kref_kotlin_UByte);
  libdengyui_kref_kotlin_UShort (*createNullableUShort)(libdengyui_KUShort);
  libdengyui_KUShort (*getNonNullValueOfUShort)(libdengyui_kref_kotlin_UShort);
  libdengyui_kref_kotlin_UInt (*createNullableUInt)(libdengyui_KUInt);
  libdengyui_KUInt (*getNonNullValueOfUInt)(libdengyui_kref_kotlin_UInt);
  libdengyui_kref_kotlin_ULong (*createNullableULong)(libdengyui_KULong);
  libdengyui_KULong (*getNonNullValueOfULong)(libdengyui_kref_kotlin_ULong);

  /* User functions. */
  struct {
    struct {
      struct {
        struct {
          struct {
            struct {
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_app_OhosApp (*_instance)();
                libdengyui_kref_com_dengying_player_platform_FileKeyValueStore (*get_kv)(libdengyui_kref_com_dengying_player_app_OhosApp thiz);
                libdengyui_kref_com_dengying_player_data_cache_PageCache (*get_pageCache)(libdengyui_kref_com_dengying_player_app_OhosApp thiz);
                libdengyui_kref_com_dengying_player_data_source_SourceRepository (*get_sources)(libdengyui_kref_com_dengying_player_app_OhosApp thiz);
                libdengyui_kref_com_dengying_player_data_store_Store (*get_store)(libdengyui_kref_com_dengying_player_app_OhosApp thiz);
                libdengyui_KInt (*init)(libdengyui_kref_com_dengying_player_app_OhosApp thiz, const char* dataDir);
              } OhosApp;
              libdengyui_KInt (*appInit)(const char* dataDir);
              libdengyui_KInt (*cancelSearch)();
              libdengyui_KInt (*clearHistory)();
              libdengyui_KInt (*fetchCategory)(const char* category, libdengyui_KInt page);
              const char* (*getHistory)();
              const char* (*homeCategories)();
              libdengyui_KInt (*refreshHistory)();
              libdengyui_KInt (*removeHistory)(const char* keyword);
              libdengyui_KInt (*searchAll)(const char* keyword);
              libdengyui_KInt (*startSuggest)(const char* keyword);
              const char* (*suggestResultJson)();
              const char* (*taskResult)();
              libdengyui_KInt (*taskRunning)();
            } app;
            struct {
              struct {
                struct {
                  libdengyui_kref_com_dengying_player_core_DiscoverKind (*get)(); /* enum entry for DoubanAnime. */
                } DoubanAnime;
                struct {
                  libdengyui_kref_com_dengying_player_core_DiscoverKind (*get)(); /* enum entry for IqiyiSuggest. */
                } IqiyiSuggest;
                libdengyui_KType* (*_type)(void);
                const char* (*get_label)(libdengyui_kref_com_dengying_player_core_DiscoverKind thiz);
              } DiscoverKind;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_DiscoverItem (*DiscoverItem)(const char* title, const char* subtitle, const char* pic, const char* year, const char* link, const char* externalId, libdengyui_kref_com_dengying_player_core_DiscoverKind kind, libdengyui_KBoolean isEntity);
                const char* (*get_externalId)(libdengyui_kref_com_dengying_player_core_DiscoverItem thiz);
                libdengyui_KBoolean (*get_isEntity)(libdengyui_kref_com_dengying_player_core_DiscoverItem thiz);
                libdengyui_kref_com_dengying_player_core_DiscoverKind (*get_kind)(libdengyui_kref_com_dengying_player_core_DiscoverItem thiz);
                const char* (*get_link)(libdengyui_kref_com_dengying_player_core_DiscoverItem thiz);
                const char* (*get_pic)(libdengyui_kref_com_dengying_player_core_DiscoverItem thiz);
                const char* (*get_subtitle)(libdengyui_kref_com_dengying_player_core_DiscoverItem thiz);
                const char* (*get_title)(libdengyui_kref_com_dengying_player_core_DiscoverItem thiz);
                const char* (*get_year)(libdengyui_kref_com_dengying_player_core_DiscoverItem thiz);
                const char* (*component1)(libdengyui_kref_com_dengying_player_core_DiscoverItem thiz);
                const char* (*component2)(libdengyui_kref_com_dengying_player_core_DiscoverItem thiz);
                const char* (*component3)(libdengyui_kref_com_dengying_player_core_DiscoverItem thiz);
                const char* (*component4)(libdengyui_kref_com_dengying_player_core_DiscoverItem thiz);
                const char* (*component5)(libdengyui_kref_com_dengying_player_core_DiscoverItem thiz);
                const char* (*component6)(libdengyui_kref_com_dengying_player_core_DiscoverItem thiz);
                libdengyui_kref_com_dengying_player_core_DiscoverKind (*component7)(libdengyui_kref_com_dengying_player_core_DiscoverItem thiz);
                libdengyui_KBoolean (*component8)(libdengyui_kref_com_dengying_player_core_DiscoverItem thiz);
                libdengyui_kref_com_dengying_player_core_DiscoverItem (*copy)(libdengyui_kref_com_dengying_player_core_DiscoverItem thiz, const char* title, const char* subtitle, const char* pic, const char* year, const char* link, const char* externalId, libdengyui_kref_com_dengying_player_core_DiscoverKind kind, libdengyui_KBoolean isEntity);
                libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_core_DiscoverItem thiz, libdengyui_kref_kotlin_Any other);
                libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_core_DiscoverItem thiz);
                const char* (*toString)(libdengyui_kref_com_dengying_player_core_DiscoverItem thiz);
              } DiscoverItem;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_DiscoverFeed (*DiscoverFeed)(libdengyui_kref_com_dengying_player_core_DiscoverKind kind, libdengyui_kref_kotlin_collections_List items, libdengyui_KInt page, const char* err);
                const char* (*get_err)(libdengyui_kref_com_dengying_player_core_DiscoverFeed thiz);
                libdengyui_KBoolean (*get_hasData)(libdengyui_kref_com_dengying_player_core_DiscoverFeed thiz);
                libdengyui_kref_kotlin_collections_List (*get_items)(libdengyui_kref_com_dengying_player_core_DiscoverFeed thiz);
                libdengyui_kref_com_dengying_player_core_DiscoverKind (*get_kind)(libdengyui_kref_com_dengying_player_core_DiscoverFeed thiz);
                libdengyui_KInt (*get_page)(libdengyui_kref_com_dengying_player_core_DiscoverFeed thiz);
                libdengyui_kref_com_dengying_player_core_DiscoverKind (*component1)(libdengyui_kref_com_dengying_player_core_DiscoverFeed thiz);
                libdengyui_kref_kotlin_collections_List (*component2)(libdengyui_kref_com_dengying_player_core_DiscoverFeed thiz);
                libdengyui_KInt (*component3)(libdengyui_kref_com_dengying_player_core_DiscoverFeed thiz);
                const char* (*component4)(libdengyui_kref_com_dengying_player_core_DiscoverFeed thiz);
                libdengyui_kref_com_dengying_player_core_DiscoverFeed (*copy)(libdengyui_kref_com_dengying_player_core_DiscoverFeed thiz, libdengyui_kref_com_dengying_player_core_DiscoverKind kind, libdengyui_kref_kotlin_collections_List items, libdengyui_KInt page, const char* err);
                libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_core_DiscoverFeed thiz, libdengyui_kref_kotlin_Any other);
                libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_core_DiscoverFeed thiz);
                const char* (*toString)(libdengyui_kref_com_dengying_player_core_DiscoverFeed thiz);
              } DiscoverFeed;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_DiscoverQuery (*_instance)();
                const char* (*get_DOUBAN_ANIME_TAG)(libdengyui_kref_com_dengying_player_core_DiscoverQuery thiz);
                libdengyui_KInt (*get_DOUBAN_PAGE_SIZE)(libdengyui_kref_com_dengying_player_core_DiscoverQuery thiz);
                const char* (*get_DOUBAN_SEARCH_BASE)(libdengyui_kref_com_dengying_player_core_DiscoverQuery thiz);
                const char* (*get_DOUBAN_SUBJECTS_BASE)(libdengyui_kref_com_dengying_player_core_DiscoverQuery thiz);
                const char* (*get_IQIYI_SUGGEST_BASE)(libdengyui_kref_com_dengying_player_core_DiscoverQuery thiz);
                const char* (*doubanAnimeUrl)(libdengyui_kref_com_dengying_player_core_DiscoverQuery thiz, libdengyui_KInt start, const char* tag);
                libdengyui_KInt (*doubanPageOf)(libdengyui_kref_com_dengying_player_core_DiscoverQuery thiz, libdengyui_KInt start);
                const char* (*doubanRankingUrl)(libdengyui_kref_com_dengying_player_core_DiscoverQuery thiz, const char* type, const char* tag, libdengyui_KInt start);
                const char* (*iqiyiSuggestUrl)(libdengyui_kref_com_dengying_player_core_DiscoverQuery thiz, const char* keyword);
              } DiscoverQuery;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_DoubanRankingTag (*_instance)();
                const char* (*get_TYPE_MOVIE)(libdengyui_kref_com_dengying_player_core_DoubanRankingTag thiz);
                const char* (*get_TYPE_MV)(libdengyui_kref_com_dengying_player_core_DoubanRankingTag thiz);
                const char* (*get_TYPE_TV)(libdengyui_kref_com_dengying_player_core_DoubanRankingTag thiz);
                libdengyui_kref_kotlin_Pair (*of)(libdengyui_kref_com_dengying_player_core_DoubanRankingTag thiz, const char* category);
              } DoubanRankingTag;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_LogTags (*_instance)();
                const char* (*get_APP)(libdengyui_kref_com_dengying_player_core_LogTags thiz);
                const char* (*get_CAST)(libdengyui_kref_com_dengying_player_core_LogTags thiz);
                const char* (*get_CMS_API)(libdengyui_kref_com_dengying_player_core_LogTags thiz);
                const char* (*get_CONFIRM)(libdengyui_kref_com_dengying_player_core_LogTags thiz);
                const char* (*get_DETAIL)(libdengyui_kref_com_dengying_player_core_LogTags thiz);
                const char* (*get_FAVORITES)(libdengyui_kref_com_dengying_player_core_LogTags thiz);
                const char* (*get_HISTORY)(libdengyui_kref_com_dengying_player_core_LogTags thiz);
                const char* (*get_HLS)(libdengyui_kref_com_dengying_player_core_LogTags thiz);
                const char* (*get_HTTP)(libdengyui_kref_com_dengying_player_core_LogTags thiz);
                const char* (*get_INDEX)(libdengyui_kref_com_dengying_player_core_LogTags thiz);
                const char* (*get_JUZHI)(libdengyui_kref_com_dengying_player_core_LogTags thiz);
                const char* (*get_PLAYER)(libdengyui_kref_com_dengying_player_core_LogTags thiz);
                const char* (*get_PREFIX)(libdengyui_kref_com_dengying_player_core_LogTags thiz);
                const char* (*get_SEARCH)(libdengyui_kref_com_dengying_player_core_LogTags thiz);
                const char* (*get_STORE)(libdengyui_kref_com_dengying_player_core_LogTags thiz);
                libdengyui_KInt (*get_TAG_MAX_LEN)(libdengyui_kref_com_dengying_player_core_LogTags thiz);
                const char* (*get_TOAST)(libdengyui_kref_com_dengying_player_core_LogTags thiz);
                const char* (*get_WEB)(libdengyui_kref_com_dengying_player_core_LogTags thiz);
                libdengyui_kref_kotlin_collections_List (*allFinalTags)(libdengyui_kref_com_dengying_player_core_LogTags thiz);
                libdengyui_kref_kotlin_collections_List (*allTags)(libdengyui_kref_com_dengying_player_core_LogTags thiz);
                libdengyui_KBoolean (*isValidTag)(libdengyui_kref_com_dengying_player_core_LogTags thiz, const char* name);
                const char* (*tag)(libdengyui_kref_com_dengying_player_core_LogTags thiz, const char* name);
              } LogTags;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_VideoItem (*VideoItem)(libdengyui_KLong vod_id, const char* vod_name, const char* vod_pic, const char* vod_remarks, const char* vod_year, const char* vod_area, const char* vod_lang, const char* vod_actor, const char* vod_director, const char* vod_content, const char* vod_play_from, const char* vod_play_url, const char* type_name, const char* vod_uri, const char* _source);
                const char* (*get__source)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*get_type_name)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*get_vod_actor)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*get_vod_area)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*get_vod_content)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*get_vod_director)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                libdengyui_KLong (*get_vod_id)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*get_vod_lang)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*get_vod_name)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*get_vod_pic)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*get_vod_play_from)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*get_vod_play_url)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*get_vod_remarks)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*get_vod_uri)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*get_vod_year)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                libdengyui_KLong (*component1)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*component10)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*component11)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*component12)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*component13)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*component14)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*component15)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*component2)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*component3)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*component4)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*component5)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*component6)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*component7)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*component8)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*component9)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                libdengyui_kref_com_dengying_player_core_VideoItem (*copy)(libdengyui_kref_com_dengying_player_core_VideoItem thiz, libdengyui_KLong vod_id, const char* vod_name, const char* vod_pic, const char* vod_remarks, const char* vod_year, const char* vod_area, const char* vod_lang, const char* vod_actor, const char* vod_director, const char* vod_content, const char* vod_play_from, const char* vod_play_url, const char* type_name, const char* vod_uri, const char* _source);
                libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_core_VideoItem thiz, libdengyui_kref_kotlin_Any other);
                libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
                const char* (*toString)(libdengyui_kref_com_dengying_player_core_VideoItem thiz);
              } VideoItem;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_Episode (*Episode)(const char* name, const char* url);
                const char* (*get_name)(libdengyui_kref_com_dengying_player_core_Episode thiz);
                const char* (*get_url)(libdengyui_kref_com_dengying_player_core_Episode thiz);
                const char* (*component1)(libdengyui_kref_com_dengying_player_core_Episode thiz);
                const char* (*component2)(libdengyui_kref_com_dengying_player_core_Episode thiz);
                libdengyui_kref_com_dengying_player_core_Episode (*copy)(libdengyui_kref_com_dengying_player_core_Episode thiz, const char* name, const char* url);
                libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_core_Episode thiz, libdengyui_kref_kotlin_Any other);
                libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_core_Episode thiz);
                const char* (*toString)(libdengyui_kref_com_dengying_player_core_Episode thiz);
              } Episode;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_PlayLine (*PlayLine)(const char* name, libdengyui_kref_kotlin_collections_List episodes);
                libdengyui_kref_kotlin_collections_List (*get_episodes)(libdengyui_kref_com_dengying_player_core_PlayLine thiz);
                const char* (*get_name)(libdengyui_kref_com_dengying_player_core_PlayLine thiz);
                const char* (*component1)(libdengyui_kref_com_dengying_player_core_PlayLine thiz);
                libdengyui_kref_kotlin_collections_List (*component2)(libdengyui_kref_com_dengying_player_core_PlayLine thiz);
                libdengyui_kref_com_dengying_player_core_PlayLine (*copy)(libdengyui_kref_com_dengying_player_core_PlayLine thiz, const char* name, libdengyui_kref_kotlin_collections_List episodes);
                libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_core_PlayLine thiz, libdengyui_kref_kotlin_Any other);
                libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_core_PlayLine thiz);
                const char* (*toString)(libdengyui_kref_com_dengying_player_core_PlayLine thiz);
              } PlayLine;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_CmsResponse (*CmsResponse)(libdengyui_KInt code, const char* msg, libdengyui_KInt page, libdengyui_KInt pagecount, const char* limit, libdengyui_KInt total, libdengyui_kref_kotlin_collections_List list, libdengyui_kref_kotlin_collections_List class_);
                libdengyui_kref_kotlin_collections_List (*get_class)(libdengyui_kref_com_dengying_player_core_CmsResponse thiz);
                libdengyui_KInt (*get_code)(libdengyui_kref_com_dengying_player_core_CmsResponse thiz);
                const char* (*get_limit)(libdengyui_kref_com_dengying_player_core_CmsResponse thiz);
                libdengyui_kref_kotlin_collections_List (*get_list)(libdengyui_kref_com_dengying_player_core_CmsResponse thiz);
                const char* (*get_msg)(libdengyui_kref_com_dengying_player_core_CmsResponse thiz);
                libdengyui_KInt (*get_page)(libdengyui_kref_com_dengying_player_core_CmsResponse thiz);
                libdengyui_KInt (*get_pagecount)(libdengyui_kref_com_dengying_player_core_CmsResponse thiz);
                libdengyui_KInt (*get_total)(libdengyui_kref_com_dengying_player_core_CmsResponse thiz);
                libdengyui_KInt (*component1)(libdengyui_kref_com_dengying_player_core_CmsResponse thiz);
                const char* (*component2)(libdengyui_kref_com_dengying_player_core_CmsResponse thiz);
                libdengyui_KInt (*component3)(libdengyui_kref_com_dengying_player_core_CmsResponse thiz);
                libdengyui_KInt (*component4)(libdengyui_kref_com_dengying_player_core_CmsResponse thiz);
                const char* (*component5)(libdengyui_kref_com_dengying_player_core_CmsResponse thiz);
                libdengyui_KInt (*component6)(libdengyui_kref_com_dengying_player_core_CmsResponse thiz);
                libdengyui_kref_kotlin_collections_List (*component7)(libdengyui_kref_com_dengying_player_core_CmsResponse thiz);
                libdengyui_kref_kotlin_collections_List (*component8)(libdengyui_kref_com_dengying_player_core_CmsResponse thiz);
                libdengyui_kref_com_dengying_player_core_CmsResponse (*copy)(libdengyui_kref_com_dengying_player_core_CmsResponse thiz, libdengyui_KInt code, const char* msg, libdengyui_KInt page, libdengyui_KInt pagecount, const char* limit, libdengyui_KInt total, libdengyui_kref_kotlin_collections_List list, libdengyui_kref_kotlin_collections_List class_);
                libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_core_CmsResponse thiz, libdengyui_kref_kotlin_Any other);
                libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_core_CmsResponse thiz);
                const char* (*toString)(libdengyui_kref_com_dengying_player_core_CmsResponse thiz);
              } CmsResponse;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_CmsClass (*CmsClass)(libdengyui_KInt type_id, const char* type_name, libdengyui_kref_kotlin_Int type_pid);
                libdengyui_KInt (*get_type_id)(libdengyui_kref_com_dengying_player_core_CmsClass thiz);
                const char* (*get_type_name)(libdengyui_kref_com_dengying_player_core_CmsClass thiz);
                libdengyui_kref_kotlin_Int (*get_type_pid)(libdengyui_kref_com_dengying_player_core_CmsClass thiz);
                libdengyui_KInt (*component1)(libdengyui_kref_com_dengying_player_core_CmsClass thiz);
                const char* (*component2)(libdengyui_kref_com_dengying_player_core_CmsClass thiz);
                libdengyui_kref_kotlin_Int (*component3)(libdengyui_kref_com_dengying_player_core_CmsClass thiz);
                libdengyui_kref_com_dengying_player_core_CmsClass (*copy)(libdengyui_kref_com_dengying_player_core_CmsClass thiz, libdengyui_KInt type_id, const char* type_name, libdengyui_kref_kotlin_Int type_pid);
                libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_core_CmsClass thiz, libdengyui_kref_kotlin_Any other);
                libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_core_CmsClass thiz);
                const char* (*toString)(libdengyui_kref_com_dengying_player_core_CmsClass thiz);
              } CmsClass;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_FavoriteItem (*FavoriteItem)(libdengyui_KLong vod_id, const char* vod_name, const char* vod_pic, const char* source, libdengyui_KLong addTime, const char* vod_uri);
                libdengyui_KLong (*get_addTime)(libdengyui_kref_com_dengying_player_core_FavoriteItem thiz);
                const char* (*get_source)(libdengyui_kref_com_dengying_player_core_FavoriteItem thiz);
                libdengyui_KLong (*get_vod_id)(libdengyui_kref_com_dengying_player_core_FavoriteItem thiz);
                const char* (*get_vod_name)(libdengyui_kref_com_dengying_player_core_FavoriteItem thiz);
                const char* (*get_vod_pic)(libdengyui_kref_com_dengying_player_core_FavoriteItem thiz);
                const char* (*get_vod_uri)(libdengyui_kref_com_dengying_player_core_FavoriteItem thiz);
                libdengyui_KLong (*component1)(libdengyui_kref_com_dengying_player_core_FavoriteItem thiz);
                const char* (*component2)(libdengyui_kref_com_dengying_player_core_FavoriteItem thiz);
                const char* (*component3)(libdengyui_kref_com_dengying_player_core_FavoriteItem thiz);
                const char* (*component4)(libdengyui_kref_com_dengying_player_core_FavoriteItem thiz);
                libdengyui_KLong (*component5)(libdengyui_kref_com_dengying_player_core_FavoriteItem thiz);
                const char* (*component6)(libdengyui_kref_com_dengying_player_core_FavoriteItem thiz);
                libdengyui_kref_com_dengying_player_core_FavoriteItem (*copy)(libdengyui_kref_com_dengying_player_core_FavoriteItem thiz, libdengyui_KLong vod_id, const char* vod_name, const char* vod_pic, const char* source, libdengyui_KLong addTime, const char* vod_uri);
                libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_core_FavoriteItem thiz, libdengyui_kref_kotlin_Any other);
                libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_core_FavoriteItem thiz);
                const char* (*toString)(libdengyui_kref_com_dengying_player_core_FavoriteItem thiz);
              } FavoriteItem;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_HistoryItem (*HistoryItem)(libdengyui_KLong vod_id, const char* vod_name, const char* vod_pic, const char* source, libdengyui_KInt episodeIndex, const char* episodeName, libdengyui_KLong position, libdengyui_KLong duration, libdengyui_KLong updateTime, const char* vod_uri);
                libdengyui_KLong (*get_duration)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz);
                libdengyui_KInt (*get_episodeIndex)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz);
                const char* (*get_episodeName)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz);
                libdengyui_KLong (*get_position)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz);
                const char* (*get_source)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz);
                libdengyui_KLong (*get_updateTime)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz);
                libdengyui_KLong (*get_vod_id)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz);
                const char* (*get_vod_name)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz);
                const char* (*get_vod_pic)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz);
                const char* (*get_vod_uri)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz);
                libdengyui_KLong (*component1)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz);
                const char* (*component10)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz);
                const char* (*component2)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz);
                const char* (*component3)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz);
                const char* (*component4)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz);
                libdengyui_KInt (*component5)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz);
                const char* (*component6)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz);
                libdengyui_KLong (*component7)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz);
                libdengyui_KLong (*component8)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz);
                libdengyui_KLong (*component9)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz);
                libdengyui_kref_com_dengying_player_core_HistoryItem (*copy)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz, libdengyui_KLong vod_id, const char* vod_name, const char* vod_pic, const char* source, libdengyui_KInt episodeIndex, const char* episodeName, libdengyui_KLong position, libdengyui_KLong duration, libdengyui_KLong updateTime, const char* vod_uri);
                libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz, libdengyui_kref_kotlin_Any other);
                libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz);
                const char* (*toString)(libdengyui_kref_com_dengying_player_core_HistoryItem thiz);
              } HistoryItem;
              struct {
                struct {
                  libdengyui_kref_com_dengying_player_core_RequestOutcome (*get)(); /* enum entry for SuccessWithData. */
                } SuccessWithData;
                struct {
                  libdengyui_kref_com_dengying_player_core_RequestOutcome (*get)(); /* enum entry for SuccessNoData. */
                } SuccessNoData;
                struct {
                  libdengyui_kref_com_dengying_player_core_RequestOutcome (*get)(); /* enum entry for ParseFailed. */
                } ParseFailed;
                struct {
                  libdengyui_kref_com_dengying_player_core_RequestOutcome (*get)(); /* enum entry for RequestFailed. */
                } RequestFailed;
                libdengyui_KType* (*_type)(void);
              } RequestOutcome;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_OutcomeFacts (*OutcomeFacts)(libdengyui_KInt status, libdengyui_KBoolean parsed, libdengyui_KInt itemCount);
                libdengyui_KInt (*get_itemCount)(libdengyui_kref_com_dengying_player_core_OutcomeFacts thiz);
                libdengyui_KBoolean (*get_parsed)(libdengyui_kref_com_dengying_player_core_OutcomeFacts thiz);
                libdengyui_KInt (*get_status)(libdengyui_kref_com_dengying_player_core_OutcomeFacts thiz);
                libdengyui_KInt (*component1)(libdengyui_kref_com_dengying_player_core_OutcomeFacts thiz);
                libdengyui_KBoolean (*component2)(libdengyui_kref_com_dengying_player_core_OutcomeFacts thiz);
                libdengyui_KInt (*component3)(libdengyui_kref_com_dengying_player_core_OutcomeFacts thiz);
                libdengyui_kref_com_dengying_player_core_OutcomeFacts (*copy)(libdengyui_kref_com_dengying_player_core_OutcomeFacts thiz, libdengyui_KInt status, libdengyui_KBoolean parsed, libdengyui_KInt itemCount);
                libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_core_OutcomeFacts thiz, libdengyui_kref_kotlin_Any other);
                libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_core_OutcomeFacts thiz);
                const char* (*toString)(libdengyui_kref_com_dengying_player_core_OutcomeFacts thiz);
              } OutcomeFacts;
              struct {
                struct {
                  libdengyui_kref_com_dengying_player_core_HealthSignal (*get)(); /* enum entry for Ok. */
                } Ok;
                struct {
                  libdengyui_kref_com_dengying_player_core_HealthSignal (*get)(); /* enum entry for SuccessNoData. */
                } SuccessNoData;
                struct {
                  libdengyui_kref_com_dengying_player_core_HealthSignal (*get)(); /* enum entry for Failed. */
                } Failed;
                libdengyui_KType* (*_type)(void);
              } HealthSignal;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_OutcomeMessages (*_instance)();
                const char* (*get_ALL_SOURCES_FAILED)(libdengyui_kref_com_dengying_player_core_OutcomeMessages thiz);
                const char* (*get_NO_NETWORK)(libdengyui_kref_com_dengying_player_core_OutcomeMessages thiz);
                const char* (*get_NO_SOURCE_HAS_IT)(libdengyui_kref_com_dengying_player_core_OutcomeMessages thiz);
              } OutcomeMessages;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_SourceSecrets (*_instance)();
                const char* (*get_GUAPI_AES_IV)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz);
                const char* (*get_GUAPI_AES_KEY)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz);
                const char* (*get_GUAPI_CHANNEL)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz);
                const char* (*get_GUAPI_DEVICE_PREFIX)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz);
                const char* (*get_GUAPI_OLD_KEY)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz);
                const char* (*get_GUAPI_PACKAGE)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz);
                const char* (*get_GUAPI_PHONE_MODEL)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz);
                const char* (*get_GUAPI_REQUEST_PUBLIC_KEY)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz);
                const char* (*get_GUAPI_RESPONSE_PRIVATE_KEY)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz);
                const char* (*get_GUAPI_SIGNATURE_SECRET)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz);
                const char* (*get_GUAPI_STREAM_REFERER)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz);
                const char* (*get_GUAPI_UA)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz);
                const char* (*get_GUAPI_VERSION_CODE)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz);
                const char* (*get_GUAPI_VERSION_NAME)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz);
                const char* (*get_JUZHI_KEY_1)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz);
                const char* (*get_JUZHI_KEY_2)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz);
                const char* (*get_JUZHI_PRIMARY_PUBLIC_KEY)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz);
                const char* (*get_JUZHI_PUBLIC_PARAMS_KEY)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz);
                const char* (*get_JUZHI_UA)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz);
                const char* (*get_MINO_UA)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz);
                const char* (*get_SHANZHA_REQUEST_PUBLIC_KEY)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz);
                const char* (*get_SHANZHA_RESPONSE_PRIVATE_KEY)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz);
                const char* (*get_WENCAI_SIGN_KEY)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz);
                const char* (*wencaiDetailSignRaw)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz, const char* vodId, const char* ts);
                const char* (*wencaiPlaySignRaw)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz, const char* vodId, const char* nid, const char* ts);
                const char* (*wencaiSearchSignRaw)(libdengyui_kref_com_dengying_player_core_SourceSecrets thiz, const char* keyword, const char* ts);
              } SourceSecrets;
              struct {
                struct {
                  libdengyui_kref_com_dengying_player_core_SourceProtocol (*get)(); /* enum entry for Cms. */
                } Cms;
                struct {
                  libdengyui_kref_com_dengying_player_core_SourceProtocol (*get)(); /* enum entry for Api2233. */
                } Api2233;
                struct {
                  libdengyui_kref_com_dengying_player_core_SourceProtocol (*get)(); /* enum entry for Mino. */
                } Mino;
                struct {
                  libdengyui_kref_com_dengying_player_core_SourceProtocol (*get)(); /* enum entry for Wencai. */
                } Wencai;
                struct {
                  libdengyui_kref_com_dengying_player_core_SourceProtocol (*get)(); /* enum entry for Duboku. */
                } Duboku;
                struct {
                  libdengyui_kref_com_dengying_player_core_SourceProtocol (*get)(); /* enum entry for Html. */
                } Html;
                struct {
                  libdengyui_kref_com_dengying_player_core_SourceProtocol (*get)(); /* enum entry for Juzhi. */
                } Juzhi;
                struct {
                  libdengyui_kref_com_dengying_player_core_SourceProtocol (*get)(); /* enum entry for Guapi. */
                } Guapi;
                struct {
                  libdengyui_kref_com_dengying_player_core_SourceProtocol (*get)(); /* enum entry for Shanzha. */
                } Shanzha;
                struct {
                  libdengyui_kref_com_dengying_player_core_SourceProtocol (*get)(); /* enum entry for Tv189. */
                } Tv189;
                libdengyui_KType* (*_type)(void);
              } SourceProtocol;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_SourceCaps (*SourceCaps)(libdengyui_KBoolean search, libdengyui_KBoolean category, libdengyui_KBoolean detail, libdengyui_KBoolean token);
                libdengyui_KBoolean (*get_category)(libdengyui_kref_com_dengying_player_core_SourceCaps thiz);
                libdengyui_KBoolean (*get_detail)(libdengyui_kref_com_dengying_player_core_SourceCaps thiz);
                libdengyui_KBoolean (*get_search)(libdengyui_kref_com_dengying_player_core_SourceCaps thiz);
                libdengyui_KBoolean (*get_token)(libdengyui_kref_com_dengying_player_core_SourceCaps thiz);
                libdengyui_KBoolean (*component1)(libdengyui_kref_com_dengying_player_core_SourceCaps thiz);
                libdengyui_KBoolean (*component2)(libdengyui_kref_com_dengying_player_core_SourceCaps thiz);
                libdengyui_KBoolean (*component3)(libdengyui_kref_com_dengying_player_core_SourceCaps thiz);
                libdengyui_KBoolean (*component4)(libdengyui_kref_com_dengying_player_core_SourceCaps thiz);
                libdengyui_kref_com_dengying_player_core_SourceCaps (*copy)(libdengyui_kref_com_dengying_player_core_SourceCaps thiz, libdengyui_KBoolean search, libdengyui_KBoolean category, libdengyui_KBoolean detail, libdengyui_KBoolean token);
                libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_core_SourceCaps thiz, libdengyui_kref_kotlin_Any other);
                libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_core_SourceCaps thiz);
                const char* (*toString)(libdengyui_kref_com_dengying_player_core_SourceCaps thiz);
              } SourceCaps;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_FetchResult (*FetchResult)(libdengyui_kref_com_dengying_player_core_RequestOutcome outcome, libdengyui_kref_kotlin_collections_List items, libdengyui_KInt page, libdengyui_KInt pageCount, libdengyui_KInt total, const char* sourceName, const char* err);
                const char* (*get_err)(libdengyui_kref_com_dengying_player_core_FetchResult thiz);
                libdengyui_KBoolean (*get_hasData)(libdengyui_kref_com_dengying_player_core_FetchResult thiz);
                libdengyui_kref_kotlin_collections_List (*get_items)(libdengyui_kref_com_dengying_player_core_FetchResult thiz);
                libdengyui_kref_com_dengying_player_core_RequestOutcome (*get_outcome)(libdengyui_kref_com_dengying_player_core_FetchResult thiz);
                libdengyui_KInt (*get_page)(libdengyui_kref_com_dengying_player_core_FetchResult thiz);
                libdengyui_KInt (*get_pageCount)(libdengyui_kref_com_dengying_player_core_FetchResult thiz);
                libdengyui_KBoolean (*get_sourceHealthy)(libdengyui_kref_com_dengying_player_core_FetchResult thiz);
                const char* (*get_sourceName)(libdengyui_kref_com_dengying_player_core_FetchResult thiz);
                libdengyui_KInt (*get_total)(libdengyui_kref_com_dengying_player_core_FetchResult thiz);
                libdengyui_kref_com_dengying_player_core_RequestOutcome (*component1)(libdengyui_kref_com_dengying_player_core_FetchResult thiz);
                libdengyui_kref_kotlin_collections_List (*component2)(libdengyui_kref_com_dengying_player_core_FetchResult thiz);
                libdengyui_KInt (*component3)(libdengyui_kref_com_dengying_player_core_FetchResult thiz);
                libdengyui_KInt (*component4)(libdengyui_kref_com_dengying_player_core_FetchResult thiz);
                libdengyui_KInt (*component5)(libdengyui_kref_com_dengying_player_core_FetchResult thiz);
                const char* (*component6)(libdengyui_kref_com_dengying_player_core_FetchResult thiz);
                const char* (*component7)(libdengyui_kref_com_dengying_player_core_FetchResult thiz);
                libdengyui_kref_com_dengying_player_core_FetchResult (*copy)(libdengyui_kref_com_dengying_player_core_FetchResult thiz, libdengyui_kref_com_dengying_player_core_RequestOutcome outcome, libdengyui_kref_kotlin_collections_List items, libdengyui_KInt page, libdengyui_KInt pageCount, libdengyui_KInt total, const char* sourceName, const char* err);
                libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_core_FetchResult thiz, libdengyui_kref_kotlin_Any other);
                libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_core_FetchResult thiz);
                const char* (*toString)(libdengyui_kref_com_dengying_player_core_FetchResult thiz);
              } FetchResult;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_PlayResolveResult (*PlayResolveResult)(const char* url, const char* headers);
                const char* (*get_headers)(libdengyui_kref_com_dengying_player_core_PlayResolveResult thiz);
                const char* (*get_url)(libdengyui_kref_com_dengying_player_core_PlayResolveResult thiz);
                const char* (*component1)(libdengyui_kref_com_dengying_player_core_PlayResolveResult thiz);
                const char* (*component2)(libdengyui_kref_com_dengying_player_core_PlayResolveResult thiz);
                libdengyui_kref_com_dengying_player_core_PlayResolveResult (*copy)(libdengyui_kref_com_dengying_player_core_PlayResolveResult thiz, const char* url, const char* headers);
                libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_core_PlayResolveResult thiz, libdengyui_kref_kotlin_Any other);
                libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_core_PlayResolveResult thiz);
                const char* (*toString)(libdengyui_kref_com_dengying_player_core_PlayResolveResult thiz);
              } PlayResolveResult;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_WebSearchRule (*WebSearchRule)(const char* method, const char* url, const char* field, const char* itemPattern, const char* base, libdengyui_kref_com_dengying_player_core_WebIdMode idMode);
                const char* (*get_base)(libdengyui_kref_com_dengying_player_core_WebSearchRule thiz);
                const char* (*get_field)(libdengyui_kref_com_dengying_player_core_WebSearchRule thiz);
                libdengyui_kref_com_dengying_player_core_WebIdMode (*get_idMode)(libdengyui_kref_com_dengying_player_core_WebSearchRule thiz);
                const char* (*get_itemPattern)(libdengyui_kref_com_dengying_player_core_WebSearchRule thiz);
                const char* (*get_method)(libdengyui_kref_com_dengying_player_core_WebSearchRule thiz);
                const char* (*get_url)(libdengyui_kref_com_dengying_player_core_WebSearchRule thiz);
                const char* (*component1)(libdengyui_kref_com_dengying_player_core_WebSearchRule thiz);
                const char* (*component2)(libdengyui_kref_com_dengying_player_core_WebSearchRule thiz);
                const char* (*component3)(libdengyui_kref_com_dengying_player_core_WebSearchRule thiz);
                const char* (*component4)(libdengyui_kref_com_dengying_player_core_WebSearchRule thiz);
                const char* (*component5)(libdengyui_kref_com_dengying_player_core_WebSearchRule thiz);
                libdengyui_kref_com_dengying_player_core_WebIdMode (*component6)(libdengyui_kref_com_dengying_player_core_WebSearchRule thiz);
                libdengyui_kref_com_dengying_player_core_WebSearchRule (*copy)(libdengyui_kref_com_dengying_player_core_WebSearchRule thiz, const char* method, const char* url, const char* field, const char* itemPattern, const char* base, libdengyui_kref_com_dengying_player_core_WebIdMode idMode);
                libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_core_WebSearchRule thiz, libdengyui_kref_kotlin_Any other);
                libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_core_WebSearchRule thiz);
                const char* (*toString)(libdengyui_kref_com_dengying_player_core_WebSearchRule thiz);
              } WebSearchRule;
              struct {
                struct {
                  libdengyui_kref_com_dengying_player_core_WebIdMode (*get)(); /* enum entry for TrailingNumber. */
                } TrailingNumber;
                struct {
                  libdengyui_kref_com_dengying_player_core_WebIdMode (*get)(); /* enum entry for SlugHash. */
                } SlugHash;
                libdengyui_KType* (*_type)(void);
              } WebIdMode;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_WebItemRule (*WebItemRule)(libdengyui_KInt nameGroup, libdengyui_KInt hrefGroup, libdengyui_KInt picGroup, libdengyui_KInt statusGroup);
                libdengyui_KInt (*get_hrefGroup)(libdengyui_kref_com_dengying_player_core_WebItemRule thiz);
                libdengyui_KInt (*get_nameGroup)(libdengyui_kref_com_dengying_player_core_WebItemRule thiz);
                libdengyui_KInt (*get_picGroup)(libdengyui_kref_com_dengying_player_core_WebItemRule thiz);
                libdengyui_KInt (*get_statusGroup)(libdengyui_kref_com_dengying_player_core_WebItemRule thiz);
                libdengyui_KInt (*component1)(libdengyui_kref_com_dengying_player_core_WebItemRule thiz);
                libdengyui_KInt (*component2)(libdengyui_kref_com_dengying_player_core_WebItemRule thiz);
                libdengyui_KInt (*component3)(libdengyui_kref_com_dengying_player_core_WebItemRule thiz);
                libdengyui_KInt (*component4)(libdengyui_kref_com_dengying_player_core_WebItemRule thiz);
                libdengyui_kref_com_dengying_player_core_WebItemRule (*copy)(libdengyui_kref_com_dengying_player_core_WebItemRule thiz, libdengyui_KInt nameGroup, libdengyui_KInt hrefGroup, libdengyui_KInt picGroup, libdengyui_KInt statusGroup);
                libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_core_WebItemRule thiz, libdengyui_kref_kotlin_Any other);
                libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_core_WebItemRule thiz);
                const char* (*toString)(libdengyui_kref_com_dengying_player_core_WebItemRule thiz);
              } WebItemRule;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_WebLineRule (*WebLineRule)(const char* pattern, libdengyui_KInt nameGroup, libdengyui_KInt anchorGroup);
                libdengyui_KInt (*get_anchorGroup)(libdengyui_kref_com_dengying_player_core_WebLineRule thiz);
                libdengyui_KInt (*get_nameGroup)(libdengyui_kref_com_dengying_player_core_WebLineRule thiz);
                const char* (*get_pattern)(libdengyui_kref_com_dengying_player_core_WebLineRule thiz);
                const char* (*component1)(libdengyui_kref_com_dengying_player_core_WebLineRule thiz);
                libdengyui_KInt (*component2)(libdengyui_kref_com_dengying_player_core_WebLineRule thiz);
                libdengyui_KInt (*component3)(libdengyui_kref_com_dengying_player_core_WebLineRule thiz);
                libdengyui_kref_com_dengying_player_core_WebLineRule (*copy)(libdengyui_kref_com_dengying_player_core_WebLineRule thiz, const char* pattern, libdengyui_KInt nameGroup, libdengyui_KInt anchorGroup);
                libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_core_WebLineRule thiz, libdengyui_kref_kotlin_Any other);
                libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_core_WebLineRule thiz);
                const char* (*toString)(libdengyui_kref_com_dengying_player_core_WebLineRule thiz);
              } WebLineRule;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_WebEpisodeRule (*WebEpisodeRule)(const char* pattern, libdengyui_KInt hrefGroup, libdengyui_KInt nameGroup, libdengyui_KInt lineGroup, libdengyui_KInt epGroup);
                libdengyui_KInt (*get_epGroup)(libdengyui_kref_com_dengying_player_core_WebEpisodeRule thiz);
                libdengyui_KInt (*get_hrefGroup)(libdengyui_kref_com_dengying_player_core_WebEpisodeRule thiz);
                libdengyui_KInt (*get_lineGroup)(libdengyui_kref_com_dengying_player_core_WebEpisodeRule thiz);
                libdengyui_KInt (*get_nameGroup)(libdengyui_kref_com_dengying_player_core_WebEpisodeRule thiz);
                const char* (*get_pattern)(libdengyui_kref_com_dengying_player_core_WebEpisodeRule thiz);
                const char* (*component1)(libdengyui_kref_com_dengying_player_core_WebEpisodeRule thiz);
                libdengyui_KInt (*component2)(libdengyui_kref_com_dengying_player_core_WebEpisodeRule thiz);
                libdengyui_KInt (*component3)(libdengyui_kref_com_dengying_player_core_WebEpisodeRule thiz);
                libdengyui_KInt (*component4)(libdengyui_kref_com_dengying_player_core_WebEpisodeRule thiz);
                libdengyui_KInt (*component5)(libdengyui_kref_com_dengying_player_core_WebEpisodeRule thiz);
                libdengyui_kref_com_dengying_player_core_WebEpisodeRule (*copy)(libdengyui_kref_com_dengying_player_core_WebEpisodeRule thiz, const char* pattern, libdengyui_KInt hrefGroup, libdengyui_KInt nameGroup, libdengyui_KInt lineGroup, libdengyui_KInt epGroup);
                libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_core_WebEpisodeRule thiz, libdengyui_kref_kotlin_Any other);
                libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_core_WebEpisodeRule thiz);
                const char* (*toString)(libdengyui_kref_com_dengying_player_core_WebEpisodeRule thiz);
              } WebEpisodeRule;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_WebPlayRule (*WebPlayRule)(const char* iframePattern, const char* urlConstPattern, const char* signConstPattern, const char* fromConstPattern, const char* mediaUrlTemplate, libdengyui_kref_kotlin_collections_Map headers, const char* jsonUrlKey);
                const char* (*get_fromConstPattern)(libdengyui_kref_com_dengying_player_core_WebPlayRule thiz);
                libdengyui_kref_kotlin_collections_Map (*get_headers)(libdengyui_kref_com_dengying_player_core_WebPlayRule thiz);
                const char* (*get_iframePattern)(libdengyui_kref_com_dengying_player_core_WebPlayRule thiz);
                const char* (*get_jsonUrlKey)(libdengyui_kref_com_dengying_player_core_WebPlayRule thiz);
                const char* (*get_mediaUrlTemplate)(libdengyui_kref_com_dengying_player_core_WebPlayRule thiz);
                const char* (*get_signConstPattern)(libdengyui_kref_com_dengying_player_core_WebPlayRule thiz);
                const char* (*get_urlConstPattern)(libdengyui_kref_com_dengying_player_core_WebPlayRule thiz);
                const char* (*component1)(libdengyui_kref_com_dengying_player_core_WebPlayRule thiz);
                const char* (*component2)(libdengyui_kref_com_dengying_player_core_WebPlayRule thiz);
                const char* (*component3)(libdengyui_kref_com_dengying_player_core_WebPlayRule thiz);
                const char* (*component4)(libdengyui_kref_com_dengying_player_core_WebPlayRule thiz);
                const char* (*component5)(libdengyui_kref_com_dengying_player_core_WebPlayRule thiz);
                libdengyui_kref_kotlin_collections_Map (*component6)(libdengyui_kref_com_dengying_player_core_WebPlayRule thiz);
                const char* (*component7)(libdengyui_kref_com_dengying_player_core_WebPlayRule thiz);
                libdengyui_kref_com_dengying_player_core_WebPlayRule (*copy)(libdengyui_kref_com_dengying_player_core_WebPlayRule thiz, const char* iframePattern, const char* urlConstPattern, const char* signConstPattern, const char* fromConstPattern, const char* mediaUrlTemplate, libdengyui_kref_kotlin_collections_Map headers, const char* jsonUrlKey);
                libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_core_WebPlayRule thiz, libdengyui_kref_kotlin_Any other);
                libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_core_WebPlayRule thiz);
                const char* (*toString)(libdengyui_kref_com_dengying_player_core_WebPlayRule thiz);
              } WebPlayRule;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_WebTokenPlayRule (*WebTokenPlayRule)(const char* userlinkPattern, const char* dataIdPattern, const char* endpoint, const char* quality, libdengyui_kref_com_dengying_player_core_WebTokenPlayAlg alg, const char* codeKey, libdengyui_KInt okCode, const char* listKey, const char* urlKey);
                libdengyui_kref_com_dengying_player_core_WebTokenPlayAlg (*get_alg)(libdengyui_kref_com_dengying_player_core_WebTokenPlayRule thiz);
                const char* (*get_codeKey)(libdengyui_kref_com_dengying_player_core_WebTokenPlayRule thiz);
                const char* (*get_dataIdPattern)(libdengyui_kref_com_dengying_player_core_WebTokenPlayRule thiz);
                const char* (*get_endpoint)(libdengyui_kref_com_dengying_player_core_WebTokenPlayRule thiz);
                const char* (*get_listKey)(libdengyui_kref_com_dengying_player_core_WebTokenPlayRule thiz);
                libdengyui_KInt (*get_okCode)(libdengyui_kref_com_dengying_player_core_WebTokenPlayRule thiz);
                const char* (*get_quality)(libdengyui_kref_com_dengying_player_core_WebTokenPlayRule thiz);
                const char* (*get_urlKey)(libdengyui_kref_com_dengying_player_core_WebTokenPlayRule thiz);
                const char* (*get_userlinkPattern)(libdengyui_kref_com_dengying_player_core_WebTokenPlayRule thiz);
                const char* (*component1)(libdengyui_kref_com_dengying_player_core_WebTokenPlayRule thiz);
                const char* (*component2)(libdengyui_kref_com_dengying_player_core_WebTokenPlayRule thiz);
                const char* (*component3)(libdengyui_kref_com_dengying_player_core_WebTokenPlayRule thiz);
                const char* (*component4)(libdengyui_kref_com_dengying_player_core_WebTokenPlayRule thiz);
                libdengyui_kref_com_dengying_player_core_WebTokenPlayAlg (*component5)(libdengyui_kref_com_dengying_player_core_WebTokenPlayRule thiz);
                const char* (*component6)(libdengyui_kref_com_dengying_player_core_WebTokenPlayRule thiz);
                libdengyui_KInt (*component7)(libdengyui_kref_com_dengying_player_core_WebTokenPlayRule thiz);
                const char* (*component8)(libdengyui_kref_com_dengying_player_core_WebTokenPlayRule thiz);
                const char* (*component9)(libdengyui_kref_com_dengying_player_core_WebTokenPlayRule thiz);
                libdengyui_kref_com_dengying_player_core_WebTokenPlayRule (*copy)(libdengyui_kref_com_dengying_player_core_WebTokenPlayRule thiz, const char* userlinkPattern, const char* dataIdPattern, const char* endpoint, const char* quality, libdengyui_kref_com_dengying_player_core_WebTokenPlayAlg alg, const char* codeKey, libdengyui_KInt okCode, const char* listKey, const char* urlKey);
                libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_core_WebTokenPlayRule thiz, libdengyui_kref_kotlin_Any other);
                libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_core_WebTokenPlayRule thiz);
                const char* (*toString)(libdengyui_kref_com_dengying_player_core_WebTokenPlayRule thiz);
              } WebTokenPlayRule;
              struct {
                struct {
                  libdengyui_kref_com_dengying_player_core_WebTokenPlayAlg (*get)(); /* enum entry for BttwoV1. */
                } BttwoV1;
                libdengyui_KType* (*_type)(void);
              } WebTokenPlayAlg;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_WebRule (*WebRule)(libdengyui_kref_com_dengying_player_core_WebSearchRule search, libdengyui_kref_com_dengying_player_core_WebItemRule item, libdengyui_kref_com_dengying_player_core_WebLineRule line, libdengyui_kref_com_dengying_player_core_WebEpisodeRule episode, libdengyui_kref_com_dengying_player_core_WebPlayRule play, libdengyui_kref_com_dengying_player_core_WebTokenPlayRule tokenPlay, libdengyui_kref_kotlin_collections_List sniff, const char* seasonListPattern, libdengyui_KBoolean reverseEpisodes, libdengyui_KBoolean playerAaaa);
                libdengyui_kref_com_dengying_player_core_WebEpisodeRule (*get_episode)(libdengyui_kref_com_dengying_player_core_WebRule thiz);
                libdengyui_kref_com_dengying_player_core_WebItemRule (*get_item)(libdengyui_kref_com_dengying_player_core_WebRule thiz);
                libdengyui_kref_com_dengying_player_core_WebLineRule (*get_line)(libdengyui_kref_com_dengying_player_core_WebRule thiz);
                libdengyui_kref_com_dengying_player_core_WebPlayRule (*get_play)(libdengyui_kref_com_dengying_player_core_WebRule thiz);
                libdengyui_KBoolean (*get_playerAaaa)(libdengyui_kref_com_dengying_player_core_WebRule thiz);
                libdengyui_KBoolean (*get_reverseEpisodes)(libdengyui_kref_com_dengying_player_core_WebRule thiz);
                libdengyui_kref_com_dengying_player_core_WebSearchRule (*get_search)(libdengyui_kref_com_dengying_player_core_WebRule thiz);
                const char* (*get_seasonListPattern)(libdengyui_kref_com_dengying_player_core_WebRule thiz);
                libdengyui_kref_kotlin_collections_List (*get_sniff)(libdengyui_kref_com_dengying_player_core_WebRule thiz);
                libdengyui_kref_com_dengying_player_core_WebTokenPlayRule (*get_tokenPlay)(libdengyui_kref_com_dengying_player_core_WebRule thiz);
                libdengyui_kref_com_dengying_player_core_WebSearchRule (*component1)(libdengyui_kref_com_dengying_player_core_WebRule thiz);
                libdengyui_KBoolean (*component10)(libdengyui_kref_com_dengying_player_core_WebRule thiz);
                libdengyui_kref_com_dengying_player_core_WebItemRule (*component2)(libdengyui_kref_com_dengying_player_core_WebRule thiz);
                libdengyui_kref_com_dengying_player_core_WebLineRule (*component3)(libdengyui_kref_com_dengying_player_core_WebRule thiz);
                libdengyui_kref_com_dengying_player_core_WebEpisodeRule (*component4)(libdengyui_kref_com_dengying_player_core_WebRule thiz);
                libdengyui_kref_com_dengying_player_core_WebPlayRule (*component5)(libdengyui_kref_com_dengying_player_core_WebRule thiz);
                libdengyui_kref_com_dengying_player_core_WebTokenPlayRule (*component6)(libdengyui_kref_com_dengying_player_core_WebRule thiz);
                libdengyui_kref_kotlin_collections_List (*component7)(libdengyui_kref_com_dengying_player_core_WebRule thiz);
                const char* (*component8)(libdengyui_kref_com_dengying_player_core_WebRule thiz);
                libdengyui_KBoolean (*component9)(libdengyui_kref_com_dengying_player_core_WebRule thiz);
                libdengyui_kref_com_dengying_player_core_WebRule (*copy)(libdengyui_kref_com_dengying_player_core_WebRule thiz, libdengyui_kref_com_dengying_player_core_WebSearchRule search, libdengyui_kref_com_dengying_player_core_WebItemRule item, libdengyui_kref_com_dengying_player_core_WebLineRule line, libdengyui_kref_com_dengying_player_core_WebEpisodeRule episode, libdengyui_kref_com_dengying_player_core_WebPlayRule play, libdengyui_kref_com_dengying_player_core_WebTokenPlayRule tokenPlay, libdengyui_kref_kotlin_collections_List sniff, const char* seasonListPattern, libdengyui_KBoolean reverseEpisodes, libdengyui_KBoolean playerAaaa);
                libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_core_WebRule thiz, libdengyui_kref_kotlin_Any other);
                libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_core_WebRule thiz);
                const char* (*toString)(libdengyui_kref_com_dengying_player_core_WebRule thiz);
              } WebRule;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_CmsSource (*CmsSource)(const char* key, const char* name, const char* api, libdengyui_KInt speed, libdengyui_KBoolean direct, libdengyui_KBoolean enabled, libdengyui_KInt tier, libdengyui_kref_com_dengying_player_core_SourceProtocol protocol, libdengyui_kref_com_dengying_player_core_SourceCaps caps, const char* searchUrl, libdengyui_kref_kotlin_collections_List mirrors, libdengyui_kref_com_dengying_player_core_WebRule rule, const char* playGap, const char* note);
                const char* (*get_api)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                libdengyui_kref_com_dengying_player_core_SourceCaps (*get_caps)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                libdengyui_KBoolean (*get_direct)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                libdengyui_KBoolean (*get_enabled)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                const char* (*get_key)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                libdengyui_kref_kotlin_collections_List (*get_mirrors)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                const char* (*get_name)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                const char* (*get_note)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                const char* (*get_playGap)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                libdengyui_kref_com_dengying_player_core_SourceProtocol (*get_protocol)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                libdengyui_kref_com_dengying_player_core_WebRule (*get_rule)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                const char* (*get_searchUrl)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                libdengyui_KInt (*get_speed)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                libdengyui_KInt (*get_tier)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                const char* (*component1)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                const char* (*component10)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                libdengyui_kref_kotlin_collections_List (*component11)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                libdengyui_kref_com_dengying_player_core_WebRule (*component12)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                const char* (*component13)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                const char* (*component14)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                const char* (*component2)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                const char* (*component3)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                libdengyui_KInt (*component4)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                libdengyui_KBoolean (*component5)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                libdengyui_KBoolean (*component6)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                libdengyui_KInt (*component7)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                libdengyui_kref_com_dengying_player_core_SourceProtocol (*component8)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                libdengyui_kref_com_dengying_player_core_SourceCaps (*component9)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                libdengyui_kref_com_dengying_player_core_CmsSource (*copy)(libdengyui_kref_com_dengying_player_core_CmsSource thiz, const char* key, const char* name, const char* api, libdengyui_KInt speed, libdengyui_KBoolean direct, libdengyui_KBoolean enabled, libdengyui_KInt tier, libdengyui_kref_com_dengying_player_core_SourceProtocol protocol, libdengyui_kref_com_dengying_player_core_SourceCaps caps, const char* searchUrl, libdengyui_kref_kotlin_collections_List mirrors, libdengyui_kref_com_dengying_player_core_WebRule rule, const char* playGap, const char* note);
                libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_core_CmsSource thiz, libdengyui_kref_kotlin_Any other);
                libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
                const char* (*toString)(libdengyui_kref_com_dengying_player_core_CmsSource thiz);
              } CmsSource;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_DirectSource (*DirectSource)(const char* key, const char* name, const char* url, libdengyui_kref_kotlin_collections_Map headers, const char* group);
                const char* (*get_group)(libdengyui_kref_com_dengying_player_core_DirectSource thiz);
                libdengyui_kref_kotlin_collections_Map (*get_headers)(libdengyui_kref_com_dengying_player_core_DirectSource thiz);
                const char* (*get_key)(libdengyui_kref_com_dengying_player_core_DirectSource thiz);
                const char* (*get_name)(libdengyui_kref_com_dengying_player_core_DirectSource thiz);
                const char* (*get_url)(libdengyui_kref_com_dengying_player_core_DirectSource thiz);
                const char* (*component1)(libdengyui_kref_com_dengying_player_core_DirectSource thiz);
                const char* (*component2)(libdengyui_kref_com_dengying_player_core_DirectSource thiz);
                const char* (*component3)(libdengyui_kref_com_dengying_player_core_DirectSource thiz);
                libdengyui_kref_kotlin_collections_Map (*component4)(libdengyui_kref_com_dengying_player_core_DirectSource thiz);
                const char* (*component5)(libdengyui_kref_com_dengying_player_core_DirectSource thiz);
                libdengyui_kref_com_dengying_player_core_DirectSource (*copy)(libdengyui_kref_com_dengying_player_core_DirectSource thiz, const char* key, const char* name, const char* url, libdengyui_kref_kotlin_collections_Map headers, const char* group);
                libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_core_DirectSource thiz, libdengyui_kref_kotlin_Any other);
                libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_core_DirectSource thiz);
                const char* (*toString)(libdengyui_kref_com_dengying_player_core_DirectSource thiz);
              } DirectSource;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_core_CmsSources (*_instance)();
                libdengyui_kref_kotlin_collections_List (*get_ALL)(libdengyui_kref_com_dengying_player_core_CmsSources thiz);
                libdengyui_kref_kotlin_collections_Map (*get_CATEGORY_ALIAS)(libdengyui_kref_com_dengying_player_core_CmsSources thiz);
                libdengyui_kref_kotlin_collections_List (*get_DIRECT)(libdengyui_kref_com_dengying_player_core_CmsSources thiz);
                libdengyui_kref_kotlin_collections_List (*get_HOME_CATEGORIES)(libdengyui_kref_com_dengying_player_core_CmsSources thiz);
                libdengyui_kref_kotlin_collections_List (*apiHosts)(libdengyui_kref_com_dengying_player_core_CmsSources thiz);
                libdengyui_kref_kotlin_collections_List (*baselines)(libdengyui_kref_com_dengying_player_core_CmsSources thiz);
                libdengyui_kref_com_dengying_player_core_CmsSource (*byKey)(libdengyui_kref_com_dengying_player_core_CmsSources thiz, const char* key);
              } CmsSources;
              libdengyui_KBoolean (*isPlayableEntry)(const char* url);
              libdengyui_kref_kotlin_collections_List (*parseEpisodes)(const char* group);
              libdengyui_kref_kotlin_collections_List (*parsePlayLines)(const char* from, const char* url);
              const char* (*aggregateNotice)(libdengyui_kref_kotlin_collections_List outcomes);
              libdengyui_kref_com_dengying_player_core_RequestOutcome (*classifyOutcome)(libdengyui_kref_com_dengying_player_core_OutcomeFacts facts);
              libdengyui_KBoolean (*isSourceHealthy)(libdengyui_kref_com_dengying_player_core_RequestOutcome thiz);
              libdengyui_KBoolean (*shouldFailover)(libdengyui_kref_com_dengying_player_core_RequestOutcome thiz);
              libdengyui_kref_com_dengying_player_core_HealthSignal (*toHealthSignal)(libdengyui_kref_com_dengying_player_core_RequestOutcome thiz);
              libdengyui_kref_com_dengying_player_core_SourceCaps (*get_CAPS_CMS)();
              libdengyui_kref_com_dengying_player_core_SourceCaps (*get_CAPS_SEARCH_ONLY)();
              libdengyui_kref_com_dengying_player_core_SourceCaps (*get_CAPS_TOKEN_SEARCH)();
              libdengyui_kref_com_dengying_player_core_SourceCaps (*get_CAPS_WEB)();
              libdengyui_kref_com_dengying_player_core_SourceCaps (*capsOf)(libdengyui_kref_com_dengying_player_core_CmsSource source);
              libdengyui_kref_com_dengying_player_core_SourceProtocol (*protocolOf)(libdengyui_kref_com_dengying_player_core_CmsSource source);
            } core;
            struct {
              struct {
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_data_cache_MediaCache (*_instance)();
                  libdengyui_KLong (*get_LIMIT_MB)(libdengyui_kref_com_dengying_player_data_cache_MediaCache thiz);
                  libdengyui_KBoolean (*get_MEDIA_CACHE_ENABLED)(libdengyui_kref_com_dengying_player_data_cache_MediaCache thiz);
                  void (*clear)(libdengyui_kref_com_dengying_player_data_cache_MediaCache thiz);
                  libdengyui_KLong (*sizeBytes)(libdengyui_kref_com_dengying_player_data_cache_MediaCache thiz);
                } MediaCache;
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_data_cache_PageCache (*PageCache)(libdengyui_kref_com_dengying_player_platform_KeyValueStore store);
                  libdengyui_KInt (*entryCount)(libdengyui_kref_com_dengying_player_data_cache_PageCache thiz);
                  libdengyui_kref_kotlin_collections_List (*get)(libdengyui_kref_com_dengying_player_data_cache_PageCache thiz, const char* category, libdengyui_KInt page, libdengyui_KLong nowMs);
                  void (*invalidateAll)(libdengyui_kref_com_dengying_player_data_cache_PageCache thiz);
                  libdengyui_KBoolean (*isStale)(libdengyui_kref_com_dengying_player_data_cache_PageCache thiz, const char* category, libdengyui_KInt page, libdengyui_KLong nowMs);
                  void (*pruneExpired)(libdengyui_kref_com_dengying_player_data_cache_PageCache thiz, libdengyui_KLong nowMs);
                  void (*put)(libdengyui_kref_com_dengying_player_data_cache_PageCache thiz, const char* category, libdengyui_KInt page, libdengyui_kref_kotlin_collections_List items, libdengyui_KLong nowMs);
                  void (*touch)(libdengyui_kref_com_dengying_player_data_cache_PageCache thiz, const char* category, libdengyui_KInt page, libdengyui_KLong nowMs);
                } PageCache;
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_data_cache_MediaCacheHost (*_instance)();
                  void (*clear)(libdengyui_kref_com_dengying_player_data_cache_MediaCacheHost thiz);
                  libdengyui_kref_kotlin_Any (*getOrCreate)(libdengyui_kref_com_dengying_player_data_cache_MediaCacheHost thiz);
                  libdengyui_KBoolean (*hasFailed)(libdengyui_kref_com_dengying_player_data_cache_MediaCacheHost thiz);
                  libdengyui_KBoolean (*isReady)(libdengyui_kref_com_dengying_player_data_cache_MediaCacheHost thiz);
                  libdengyui_KLong (*sizeBytes)(libdengyui_kref_com_dengying_player_data_cache_MediaCacheHost thiz);
                } MediaCacheHost;
              } cache;
              struct {
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_data_json_JsonX (*_instance)();
                  libdengyui_kref_kotlinx_serialization_json_Json (*get_json)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz);
                  libdengyui_kref_kotlinx_serialization_json_JsonArray (*arr)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonElement e);
                  libdengyui_KBoolean (*bool_)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonObject o, const char* key, libdengyui_KBoolean fallback);
                  libdengyui_KBoolean (*boolOf)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonElement e, libdengyui_KBoolean fallback);
                  libdengyui_kref_kotlinx_serialization_json_JsonArray (*buildArray)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlin_collections_List items);
                  libdengyui_kref_kotlinx_serialization_json_JsonObject (*buildObject)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlin_Array pairs);
                  libdengyui_KDouble (*double_)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonObject o, const char* key, libdengyui_KDouble fallback);
                  libdengyui_KDouble (*doubleOf)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonElement e, libdengyui_KDouble fallback);
                  libdengyui_KLong (*firstLong)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonObject o, libdengyui_kref_kotlin_Array keys);
                  const char* (*firstStr)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonObject o, libdengyui_kref_kotlin_Array keys);
                  libdengyui_KBoolean (*has)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonObject o, const char* key);
                  libdengyui_KInt (*int_)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonObject o, const char* key, libdengyui_KInt fallback);
                  libdengyui_KInt (*intOf)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonElement e, libdengyui_KInt fallback);
                  libdengyui_KBoolean (*isNull)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonElement e);
                  libdengyui_KBoolean (*isString)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonElement e);
                  libdengyui_kref_kotlin_collections_List (*keys)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonObject o);
                  libdengyui_KInt (*len)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonArray a);
                  libdengyui_KLong (*long_)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonObject o, const char* key, libdengyui_KLong fallback);
                  libdengyui_KLong (*longOf)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonElement e, libdengyui_KLong fallback);
                  libdengyui_kref_kotlinx_serialization_json_JsonObject (*obj)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonElement e);
                  libdengyui_kref_kotlinx_serialization_json_JsonArray (*optArray)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonObject o, const char* key);
                  libdengyui_kref_kotlinx_serialization_json_JsonArray (*optArrayAt)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonArray a, libdengyui_KInt i);
                  libdengyui_kref_kotlinx_serialization_json_JsonObject (*optObject)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonObject o, const char* key);
                  libdengyui_kref_kotlinx_serialization_json_JsonObject (*optObjectAt)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonArray a, libdengyui_KInt i);
                  libdengyui_kref_kotlinx_serialization_json_JsonElement (*parseAny)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, const char* text);
                  libdengyui_kref_kotlinx_serialization_json_JsonArray (*parseArray)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, const char* text);
                  libdengyui_kref_kotlinx_serialization_json_JsonObject (*parseObject)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, const char* text);
                  const char* (*str)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonObject o, const char* key, const char* fallback);
                  const char* (*strAt)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonArray a, libdengyui_KInt i, const char* fallback);
                  const char* (*textOf)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlinx_serialization_json_JsonElement e, const char* fallback);
                  libdengyui_kref_kotlinx_serialization_json_JsonElement (*value)(libdengyui_kref_com_dengying_player_data_json_JsonX thiz, libdengyui_kref_kotlin_Any v);
                } JsonX;
                struct {
                  struct {
                    struct {
                      libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_Kind (*get)(); /* enum entry for Ok. */
                    } Ok;
                    struct {
                      libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_Kind (*get)(); /* enum entry for Corrupt. */
                    } Corrupt;
                    struct {
                      libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_Kind (*get)(); /* enum entry for NeedsUpgrade. */
                    } NeedsUpgrade;
                    libdengyui_KType* (*_type)(void);
                  } Kind;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_DecodeResult (*DecodeResult)(libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_Kind kind, libdengyui_KInt version, const char* payload, libdengyui_KBoolean needsUpgrade);
                    libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_Kind (*get_kind)(libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_DecodeResult thiz);
                    libdengyui_KBoolean (*get_needsUpgrade)(libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_DecodeResult thiz);
                    const char* (*get_payload)(libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_DecodeResult thiz);
                    libdengyui_KInt (*get_version)(libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_DecodeResult thiz);
                    libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_Kind (*component1)(libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_DecodeResult thiz);
                    libdengyui_KInt (*component2)(libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_DecodeResult thiz);
                    const char* (*component3)(libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_DecodeResult thiz);
                    libdengyui_KBoolean (*component4)(libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_DecodeResult thiz);
                    libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_DecodeResult (*copy)(libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_DecodeResult thiz, libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_Kind kind, libdengyui_KInt version, const char* payload, libdengyui_KBoolean needsUpgrade);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_DecodeResult thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_DecodeResult thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_DecodeResult thiz);
                  } DecodeResult;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_data_json_StoreEnvelope (*_instance)();
                  libdengyui_KInt (*get_CURRENT_VERSION)(libdengyui_kref_com_dengying_player_data_json_StoreEnvelope thiz);
                  const char* (*get_EMPTY_PAYLOAD)(libdengyui_kref_com_dengying_player_data_json_StoreEnvelope thiz);
                  const char* (*get_KEY_CORRUPT)(libdengyui_kref_com_dengying_player_data_json_StoreEnvelope thiz);
                  libdengyui_KBoolean (*canOverwrite)(libdengyui_kref_com_dengying_player_data_json_StoreEnvelope thiz, const char* raw);
                  libdengyui_kref_com_dengying_player_data_json_StoreEnvelope_DecodeResult (*decodePayload)(libdengyui_kref_com_dengying_player_data_json_StoreEnvelope thiz, const char* raw);
                  const char* (*encodePayload)(libdengyui_kref_com_dengying_player_data_json_StoreEnvelope thiz, const char* payloadJson);
                  libdengyui_KBoolean (*needsLazyUpgrade)(libdengyui_kref_com_dengying_player_data_json_StoreEnvelope thiz, const char* raw);
                } StoreEnvelope;
              } json;
              struct {
                struct {
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_net_HlsUtil_SharePageFacts (*SharePageFacts)(libdengyui_KInt htmlLen, libdengyui_KBoolean hasM3u8, libdengyui_KBoolean hasMp4, libdengyui_KBoolean hasIframe, libdengyui_KBoolean hasPlayerKeyword, libdengyui_KBoolean hasScript, libdengyui_KBoolean matched);
                    libdengyui_KBoolean (*get_hasIframe)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_SharePageFacts thiz);
                    libdengyui_KBoolean (*get_hasM3u8)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_SharePageFacts thiz);
                    libdengyui_KBoolean (*get_hasMp4)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_SharePageFacts thiz);
                    libdengyui_KBoolean (*get_hasPlayerKeyword)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_SharePageFacts thiz);
                    libdengyui_KBoolean (*get_hasScript)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_SharePageFacts thiz);
                    libdengyui_KInt (*get_htmlLen)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_SharePageFacts thiz);
                    libdengyui_KBoolean (*get_matched)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_SharePageFacts thiz);
                    libdengyui_KInt (*component1)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_SharePageFacts thiz);
                    libdengyui_KBoolean (*component2)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_SharePageFacts thiz);
                    libdengyui_KBoolean (*component3)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_SharePageFacts thiz);
                    libdengyui_KBoolean (*component4)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_SharePageFacts thiz);
                    libdengyui_KBoolean (*component5)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_SharePageFacts thiz);
                    libdengyui_KBoolean (*component6)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_SharePageFacts thiz);
                    libdengyui_KBoolean (*component7)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_SharePageFacts thiz);
                    libdengyui_kref_com_dengying_player_data_net_HlsUtil_SharePageFacts (*copy)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_SharePageFacts thiz, libdengyui_KInt htmlLen, libdengyui_KBoolean hasM3u8, libdengyui_KBoolean hasMp4, libdengyui_KBoolean hasIframe, libdengyui_KBoolean hasPlayerKeyword, libdengyui_KBoolean hasScript, libdengyui_KBoolean matched);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_SharePageFacts thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_SharePageFacts thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_SharePageFacts thiz);
                  } SharePageFacts;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolvedPlay (*ResolvedPlay)(const char* url, libdengyui_kref_kotlin_collections_List variants, const char* selected);
                    const char* (*get_selected)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolvedPlay thiz);
                    const char* (*get_url)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolvedPlay thiz);
                    libdengyui_kref_kotlin_collections_List (*get_variants)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolvedPlay thiz);
                    const char* (*component1)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolvedPlay thiz);
                    libdengyui_kref_kotlin_collections_List (*component2)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolvedPlay thiz);
                    const char* (*component3)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolvedPlay thiz);
                    libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolvedPlay (*copy)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolvedPlay thiz, const char* url, libdengyui_kref_kotlin_collections_List variants, const char* selected);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolvedPlay thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolvedPlay thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolvedPlay thiz);
                  } ResolvedPlay;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolveOutcome (*ResolveOutcome)(const char* url, libdengyui_KBoolean resolved);
                    libdengyui_KBoolean (*get_resolved)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolveOutcome thiz);
                    const char* (*get_url)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolveOutcome thiz);
                    const char* (*component1)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolveOutcome thiz);
                    libdengyui_KBoolean (*component2)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolveOutcome thiz);
                    libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolveOutcome (*copy)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolveOutcome thiz, const char* url, libdengyui_KBoolean resolved);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolveOutcome thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolveOutcome thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ResolveOutcome thiz);
                  } ResolveOutcome;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_net_HlsUtil_ReachResult (*ReachResult)(libdengyui_KBoolean m3u8Ok, libdengyui_KInt m3u8Status, libdengyui_KBoolean segOk, libdengyui_KInt segStatus, const char* segHost);
                    libdengyui_KBoolean (*get_m3u8Ok)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ReachResult thiz);
                    libdengyui_KInt (*get_m3u8Status)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ReachResult thiz);
                    const char* (*get_segHost)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ReachResult thiz);
                    libdengyui_KBoolean (*get_segOk)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ReachResult thiz);
                    libdengyui_KInt (*get_segStatus)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ReachResult thiz);
                    libdengyui_KBoolean (*component1)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ReachResult thiz);
                    libdengyui_KInt (*component2)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ReachResult thiz);
                    libdengyui_KBoolean (*component3)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ReachResult thiz);
                    libdengyui_KInt (*component4)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ReachResult thiz);
                    const char* (*component5)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ReachResult thiz);
                    libdengyui_kref_com_dengying_player_data_net_HlsUtil_ReachResult (*copy)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ReachResult thiz, libdengyui_KBoolean m3u8Ok, libdengyui_KInt m3u8Status, libdengyui_KBoolean segOk, libdengyui_KInt segStatus, const char* segHost);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ReachResult thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ReachResult thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_data_net_HlsUtil_ReachResult thiz);
                  } ReachResult;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_data_net_HlsUtil (*_instance)();
                  libdengyui_KBoolean (*isM3u8)(libdengyui_kref_com_dengying_player_data_net_HlsUtil thiz, const char* url);
                  libdengyui_KBoolean (*isSharePage)(libdengyui_kref_com_dengying_player_data_net_HlsUtil thiz, const char* url);
                  const char* (*originOf)(libdengyui_kref_com_dengying_player_data_net_HlsUtil thiz, const char* url);
                } HlsUtil;
                struct {
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpResult (*HttpResult)(libdengyui_KInt status, const char* body, libdengyui_KLong elapsedMs);
                    const char* (*get_body)(libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpResult thiz);
                    libdengyui_KLong (*get_elapsedMs)(libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpResult thiz);
                    libdengyui_KInt (*get_status)(libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpResult thiz);
                    libdengyui_KInt (*component1)(libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpResult thiz);
                    const char* (*component2)(libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpResult thiz);
                    libdengyui_KLong (*component3)(libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpResult thiz);
                    libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpResult (*copy)(libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpResult thiz, libdengyui_KInt status, const char* body, libdengyui_KLong elapsedMs);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpResult thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpResult thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpResult thiz);
                  } HttpResult;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpBinaryResult (*HttpBinaryResult)(libdengyui_KInt status, libdengyui_kref_kotlin_ByteArray bytes, libdengyui_KLong elapsedMs);
                    libdengyui_kref_kotlin_ByteArray (*get_bytes)(libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpBinaryResult thiz);
                    libdengyui_KLong (*get_elapsedMs)(libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpBinaryResult thiz);
                    libdengyui_KInt (*get_status)(libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpBinaryResult thiz);
                    libdengyui_KInt (*component1)(libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpBinaryResult thiz);
                    libdengyui_kref_kotlin_ByteArray (*component2)(libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpBinaryResult thiz);
                    libdengyui_KLong (*component3)(libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpBinaryResult thiz);
                    libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpBinaryResult (*copy)(libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpBinaryResult thiz, libdengyui_KInt status, libdengyui_kref_kotlin_ByteArray bytes, libdengyui_KLong elapsedMs);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpBinaryResult thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpBinaryResult thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_data_net_HttpClient_HttpBinaryResult thiz);
                  } HttpBinaryResult;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_data_net_HttpClient (*_instance)();
                  const char* (*get_UA_PC)(libdengyui_kref_com_dengying_player_data_net_HttpClient thiz);
                  const char* (*get_UA_SYSTEM)(libdengyui_kref_com_dengying_player_data_net_HttpClient thiz);
                  libdengyui_kref_kotlinx_serialization_json_JsonObject (*safeJson)(libdengyui_kref_com_dengying_player_data_net_HttpClient thiz, const char* text);
                  libdengyui_kref_kotlinx_serialization_json_JsonArray (*safeJsonArray)(libdengyui_kref_com_dengying_player_data_net_HttpClient thiz, const char* text);
                } HttpClient;
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_data_net_RawResponse (*RawResponse)(libdengyui_KInt status, const char* text, libdengyui_kref_kotlin_ByteArray bytes);
                  libdengyui_kref_kotlin_ByteArray (*get_bytes)(libdengyui_kref_com_dengying_player_data_net_RawResponse thiz);
                  libdengyui_KInt (*get_status)(libdengyui_kref_com_dengying_player_data_net_RawResponse thiz);
                  const char* (*get_text)(libdengyui_kref_com_dengying_player_data_net_RawResponse thiz);
                  libdengyui_KInt (*component1)(libdengyui_kref_com_dengying_player_data_net_RawResponse thiz);
                  const char* (*component2)(libdengyui_kref_com_dengying_player_data_net_RawResponse thiz);
                  libdengyui_kref_kotlin_ByteArray (*component3)(libdengyui_kref_com_dengying_player_data_net_RawResponse thiz);
                  libdengyui_kref_com_dengying_player_data_net_RawResponse (*copy)(libdengyui_kref_com_dengying_player_data_net_RawResponse thiz, libdengyui_KInt status, const char* text, libdengyui_kref_kotlin_ByteArray bytes);
                  libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_data_net_RawResponse thiz, libdengyui_kref_kotlin_Any other);
                  libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_data_net_RawResponse thiz);
                  const char* (*toString)(libdengyui_kref_com_dengying_player_data_net_RawResponse thiz);
                } RawResponse;
                struct {
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_net_NetPriority_Key (*_instance)();
                    libdengyui_kref_com_dengying_player_data_net_NetPriority (*get_Background)(libdengyui_kref_com_dengying_player_data_net_NetPriority_Key thiz);
                    libdengyui_kref_com_dengying_player_data_net_NetPriority (*get_Foreground)(libdengyui_kref_com_dengying_player_data_net_NetPriority_Key thiz);
                  } Key;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_KBoolean (*get_background)(libdengyui_kref_com_dengying_player_data_net_NetPriority thiz);
                } NetPriority;
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_data_net_HttpTransport (*_instance)();
                  libdengyui_KBoolean (*beginTest)(libdengyui_kref_com_dengying_player_data_net_HttpTransport thiz);
                  void (*endTest)(libdengyui_kref_com_dengying_player_data_net_HttpTransport thiz);
                  libdengyui_KBoolean (*testRunning)(libdengyui_kref_com_dengying_player_data_net_HttpTransport thiz);
                  libdengyui_KLong (*testStartedAt)(libdengyui_kref_com_dengying_player_data_net_HttpTransport thiz);
                } HttpTransport;
                const char* (*httpTestResult)();
                libdengyui_KInt (*httpTestRunning)();
                libdengyui_KInt (*startHttpTest)(const char* url);
                void (*httpComplete)(libdengyui_KLong id, libdengyui_KInt status, libdengyui_KInt binary, void* payloadBase64, libdengyui_KInt payloadLen);
              } net;
              struct {
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_data_source_SourceOverrideCodec (*_instance)();
                  libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload (*normalizePayload)(libdengyui_kref_com_dengying_player_data_source_SourceOverrideCodec thiz, const char* json);
                  const char* (*toJson)(libdengyui_kref_com_dengying_player_data_source_SourceOverrideCodec thiz, libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload payload);
                } SourceOverrideCodec;
                struct {
                  struct {
                    struct {
                      libdengyui_KType* (*_type)(void);
                      libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage (*PlayPage)(const char* flag, libdengyui_KInt encrypt, const char* url, const char* urlNext, const char* from, const char* vodId, const char* vodName, libdengyui_KInt sid, libdengyui_KInt nid);
                      const char* (*get_decodedUrl)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz);
                      const char* (*get_decodedUrlNext)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz);
                      libdengyui_KInt (*get_encrypt)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz);
                      const char* (*get_flag)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz);
                      const char* (*get_from)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz);
                      libdengyui_KInt (*get_nid)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz);
                      libdengyui_KInt (*get_sid)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz);
                      const char* (*get_url)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz);
                      const char* (*get_urlNext)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz);
                      const char* (*get_vodId)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz);
                      const char* (*get_vodName)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz);
                      const char* (*component1)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz);
                      libdengyui_KInt (*component2)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz);
                      const char* (*component3)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz);
                      const char* (*component4)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz);
                      const char* (*component5)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz);
                      const char* (*component6)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz);
                      const char* (*component7)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz);
                      libdengyui_KInt (*component8)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz);
                      libdengyui_KInt (*component9)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz);
                      libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage (*copy)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz, const char* flag, libdengyui_KInt encrypt, const char* url, const char* urlNext, const char* from, const char* vodId, const char* vodName, libdengyui_KInt sid, libdengyui_KInt nid);
                      libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz, libdengyui_kref_kotlin_Any other);
                      libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz);
                      const char* (*toString)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage thiz);
                    } PlayPage;
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser (*_instance)();
                    libdengyui_KInt (*get_ENCRYPT_BASE64)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser thiz);
                    libdengyui_KInt (*get_ENCRYPT_NONE)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser thiz);
                    libdengyui_KInt (*get_ENCRYPT_UNESCAPE)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser thiz);
                    const char* (*get_PLAYER_VAR)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser thiz);
                    const char* (*decodeUrl)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser thiz, const char* url, libdengyui_KInt encrypt);
                    const char* (*extractObject)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser thiz, const char* html);
                    libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser_PlayPage (*parse)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser thiz, const char* html);
                    const char* (*unescape)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayPageParser thiz, const char* value);
                  } Ap2233PlayPageParser;
                  struct {
                    struct {
                      struct {
                        libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_TokenKind (*get)(); /* enum entry for Bba. */
                      } Bba;
                      struct {
                        libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_TokenKind (*get)(); /* enum entry for Nby. */
                      } Nby;
                      struct {
                        libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_TokenKind (*get)(); /* enum entry for Url. */
                      } Url;
                      struct {
                        libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_TokenKind (*get)(); /* enum entry for Unknown. */
                      } Unknown;
                      libdengyui_KType* (*_type)(void);
                    } TokenKind;
                    struct {
                      libdengyui_KType* (*_type)(void);
                      libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_Scan (*Scan)(libdengyui_KInt total, libdengyui_KInt bba, libdengyui_KInt nby, libdengyui_KInt url, libdengyui_KInt unknown);
                      libdengyui_KInt (*get_bba)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_Scan thiz);
                      libdengyui_KInt (*get_nby)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_Scan thiz);
                      libdengyui_KInt (*get_total)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_Scan thiz);
                      libdengyui_KInt (*get_unknown)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_Scan thiz);
                      libdengyui_KInt (*get_url)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_Scan thiz);
                      libdengyui_KInt (*component1)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_Scan thiz);
                      libdengyui_KInt (*component2)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_Scan thiz);
                      libdengyui_KInt (*component3)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_Scan thiz);
                      libdengyui_KInt (*component4)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_Scan thiz);
                      libdengyui_KInt (*component5)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_Scan thiz);
                      libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_Scan (*copy)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_Scan thiz, libdengyui_KInt total, libdengyui_KInt bba, libdengyui_KInt nby, libdengyui_KInt url, libdengyui_KInt unknown);
                      libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_Scan thiz, libdengyui_kref_kotlin_Any other);
                      libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_Scan thiz);
                      const char* (*toString)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_Scan thiz);
                    } Scan;
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser (*_instance)();
                    const char* (*get_BBA_CONST_PREFIX_HEX)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser thiz);
                    const char* (*get_PREFIX_BBA)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser thiz);
                    const char* (*get_PREFIX_NBY)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser thiz);
                    libdengyui_KChar (*get_SEP_NBY_TAIL)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser thiz);
                    const char* (*bodyHexOf)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser thiz, const char* token);
                    const char* (*gapNoticeOf)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser thiz, const char* token);
                    libdengyui_KBoolean (*isPlayable)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser thiz, const char* token);
                    libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_TokenKind (*kindOf)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser thiz, const char* token);
                    libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser_Scan (*scan)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser thiz, const char* playUrl);
                    const char* (*tailHexOf)(libdengyui_kref_com_dengying_player_data_source_adapters_Ap2233PlayParser thiz, const char* token);
                  } Ap2233PlayParser;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_adapters_BttwoSign (*_instance)();
                    const char* (*playUrl)(libdengyui_kref_com_dengying_player_data_source_adapters_BttwoSign thiz, const char* host, const char* dataId, const char* v, const char* quality, libdengyui_KLong timestamp, const char* userlink);
                    const char* (*sign)(libdengyui_kref_com_dengying_player_data_source_adapters_BttwoSign thiz, const char* dataId, const char* timestamp, const char* v);
                    const char* (*token)(libdengyui_kref_com_dengying_player_data_source_adapters_BttwoSign thiz, const char* userlink);
                  } BttwoSign;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_adapters_CmsAdapter (*_instance)();
                  } CmsAdapter;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_adapters_CmsParser (*_instance)();
                    libdengyui_kref_com_dengying_player_core_FetchResult (*api2233Search)(libdengyui_kref_com_dengying_player_data_source_adapters_CmsParser thiz, const char* body, libdengyui_kref_com_dengying_player_core_CmsSource source);
                    libdengyui_kref_kotlin_collections_List (*classes)(libdengyui_kref_com_dengying_player_data_source_adapters_CmsParser thiz, const char* body);
                    libdengyui_kref_com_dengying_player_core_VideoItem (*detail)(libdengyui_kref_com_dengying_player_data_source_adapters_CmsParser thiz, const char* body, libdengyui_kref_com_dengying_player_core_CmsSource source, libdengyui_KLong vodId);
                    libdengyui_kref_com_dengying_player_core_FetchResult (*failed)(libdengyui_kref_com_dengying_player_data_source_adapters_CmsParser thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, libdengyui_KInt page, const char* err, libdengyui_kref_com_dengying_player_core_RequestOutcome outcome);
                    libdengyui_kref_com_dengying_player_core_FetchResult (*fill)(libdengyui_kref_com_dengying_player_data_source_adapters_CmsParser thiz, const char* body, libdengyui_kref_com_dengying_player_core_CmsSource source, libdengyui_KInt page);
                    libdengyui_kref_com_dengying_player_core_VideoItem (*parseItem)(libdengyui_kref_com_dengying_player_data_source_adapters_CmsParser thiz, libdengyui_kref_kotlinx_serialization_json_JsonObject o, const char* sourceKey);
                  } CmsParser;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_adapters_DubokuAdapter (*_instance)();
                  } DubokuAdapter;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_adapters_DubokuParser (*_instance)();
                    libdengyui_kref_com_dengying_player_core_FetchResult (*failed)(libdengyui_kref_com_dengying_player_data_source_adapters_DubokuParser thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, libdengyui_KInt page, const char* err, libdengyui_kref_com_dengying_player_core_RequestOutcome outcome);
                    libdengyui_kref_com_dengying_player_core_VideoItem (*parseDetail)(libdengyui_kref_com_dengying_player_data_source_adapters_DubokuParser thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, const char* body, libdengyui_KLong vodId);
                    libdengyui_kref_com_dengying_player_core_PlayResolveResult (*parsePlay)(libdengyui_kref_com_dengying_player_data_source_adapters_DubokuParser thiz, const char* body);
                    libdengyui_kref_com_dengying_player_core_FetchResult (*parseSearch)(libdengyui_kref_com_dengying_player_data_source_adapters_DubokuParser thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, const char* body);
                    const char* (*resolveTarget)(libdengyui_kref_com_dengying_player_data_source_adapters_DubokuParser thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, const char* token);
                    libdengyui_KLong (*trailingNumber)(libdengyui_kref_com_dengying_player_data_source_adapters_DubokuParser thiz, const char* path);
                  } DubokuParser;
                  struct {
                    struct {
                      libdengyui_KType* (*_type)(void);
                      libdengyui_kref_com_dengying_player_data_source_adapters_GuapiAdapter_Session (*Session)(const char* deviceId, const char* deviceKey, const char* token, const char* tokenId);
                      const char* (*get_deviceId)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiAdapter_Session thiz);
                      const char* (*get_deviceKey)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiAdapter_Session thiz);
                      const char* (*get_token)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiAdapter_Session thiz);
                      const char* (*get_tokenId)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiAdapter_Session thiz);
                      const char* (*component1)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiAdapter_Session thiz);
                      const char* (*component2)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiAdapter_Session thiz);
                      const char* (*component3)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiAdapter_Session thiz);
                      const char* (*component4)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiAdapter_Session thiz);
                      libdengyui_kref_com_dengying_player_data_source_adapters_GuapiAdapter_Session (*copy)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiAdapter_Session thiz, const char* deviceId, const char* deviceKey, const char* token, const char* tokenId);
                      libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiAdapter_Session thiz, libdengyui_kref_kotlin_Any other);
                      libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiAdapter_Session thiz);
                      const char* (*toString)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiAdapter_Session thiz);
                    } Session;
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_adapters_GuapiAdapter (*_instance)();
                  } GuapiAdapter;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_adapters_GuapiParser (*_instance)();
                    libdengyui_KInt (*get_OK_CODE)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiParser thiz);
                    const char* (*get_SHOW_TYPE_LOCKED)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiParser thiz);
                    const char* (*decryptResponse)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiParser thiz, const char* body);
                    const char* (*expandParam)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiParser thiz, const char* param);
                    libdengyui_kref_com_dengying_player_core_FetchResult (*failed)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiParser thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, const char* err, libdengyui_kref_com_dengying_player_core_RequestOutcome outcome);
                    libdengyui_KLong (*idOf)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiParser thiz, libdengyui_kref_kotlinx_serialization_json_JsonElement e);
                    const char* (*keysField)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiParser thiz);
                    libdengyui_kref_com_dengying_player_core_VideoItem (*parseDetail)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiParser thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, const char* text, libdengyui_KLong vodId);
                    libdengyui_kref_com_dengying_player_core_PlayResolveResult (*parsePlay)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiParser thiz, const char* text);
                    libdengyui_kref_com_dengying_player_core_FetchResult (*parseSearch)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiParser thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, const char* text);
                    libdengyui_kref_com_dengying_player_core_VideoItem (*parseVurl)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiParser thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, const char* text, libdengyui_KLong vodId, const char* name, const char* pic);
                    const char* (*requestKey)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiParser thiz, const char* bizJson);
                    const char* (*signature)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiParser thiz, const char* core);
                    const char* (*signatureCore)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiParser thiz, const char* tokenId, const char* token, const char* ts, const char* requestKey, const char* keys);
                    const char* (*stripHtml)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiParser thiz, const char* value);
                    const char* (*unwrapEnvelope)(libdengyui_kref_com_dengying_player_data_source_adapters_GuapiParser thiz, const char* body);
                  } GuapiParser;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiAdapter (*_instance)();
                  } JuzhiAdapter;
                  struct {
                    struct {
                      libdengyui_KType* (*_type)(void);
                      libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser_PlayParams (*PlayParams)(const char* playUrl, const char* vodPlayFrom);
                      const char* (*get_playUrl)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser_PlayParams thiz);
                      const char* (*get_vodPlayFrom)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser_PlayParams thiz);
                      const char* (*component1)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser_PlayParams thiz);
                      const char* (*component2)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser_PlayParams thiz);
                      libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser_PlayParams (*copy)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser_PlayParams thiz, const char* playUrl, const char* vodPlayFrom);
                      libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser_PlayParams thiz, libdengyui_kref_kotlin_Any other);
                      libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser_PlayParams thiz);
                      const char* (*toString)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser_PlayParams thiz);
                    } PlayParams;
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser (*_instance)();
                    const char* (*get_V_APP)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser thiz);
                    libdengyui_kref_kotlin_ByteArray (*apiDataOf)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser thiz, libdengyui_kref_kotlin_ByteArray bytes);
                    const char* (*deviceParamsJson)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser thiz, libdengyui_KLong ts, const char* randomStr, const char* aesSign, const char* sig, const char* vApp, const char* uuid);
                    libdengyui_kref_com_dengying_player_core_FetchResult (*failed)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, const char* err, libdengyui_kref_com_dengying_player_core_RequestOutcome outcome);
                    libdengyui_KLong (*idOf)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser thiz, libdengyui_kref_kotlin_collections_List fields);
                    libdengyui_KBoolean (*isMediaUrl)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser thiz, const char* v);
                    const char* (*newUuid)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser thiz);
                    libdengyui_kref_com_dengying_player_core_VideoItem (*parseDetail)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, libdengyui_kref_kotlin_ByteArray data, libdengyui_KLong vodId);
                    libdengyui_kref_com_dengying_player_core_PlayResolveResult (*parsePlay)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser thiz, libdengyui_kref_kotlin_ByteArray data);
                    libdengyui_kref_com_dengying_player_core_FetchResult (*parseSearch)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, libdengyui_kref_kotlin_ByteArray data);
                    const char* (*picOf)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser thiz, libdengyui_kref_kotlin_ByteArray coverBytes);
                    libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser_PlayParams (*playParamsOf)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser thiz, const char* token, const char* from);
                    libdengyui_kref_kotlin_ByteArray (*requestBodyBytes)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser thiz, libdengyui_kref_kotlin_collections_Map fields, libdengyui_KLong ts, const char* random8);
                    const char* (*rnd)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser thiz, libdengyui_KInt n);
                    libdengyui_kref_kotlin_collections_Map (*v5Headers)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser thiz, const char* secondLevelPublic, libdengyui_KLong ts, const char* randomStr, const char* uuid);
                    const char* (*zoneKeyOf)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser thiz, libdengyui_kref_kotlin_ByteArray bytes);
                    libdengyui_kref_kotlin_ByteArray (*zoneRequestBytes)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiParser thiz, libdengyui_KLong ts, const char* randomStr);
                  } JuzhiParser;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiRoot (*_instance)();
                    void (*invalidate)(libdengyui_kref_com_dengying_player_data_source_adapters_JuzhiRoot thiz);
                  } JuzhiRoot;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_adapters_MinoAdapter (*_instance)();
                  } MinoAdapter;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_adapters_MinoParser (*_instance)();
                    libdengyui_KInt (*get_QUOTA_CODE)(libdengyui_kref_com_dengying_player_data_source_adapters_MinoParser thiz);
                    const char* (*buildParseBody)(libdengyui_kref_com_dengying_player_data_source_adapters_MinoParser thiz, const char* token, const char* from, libdengyui_KLong vodId);
                    libdengyui_kref_com_dengying_player_core_VideoItem (*detail)(libdengyui_kref_com_dengying_player_data_source_adapters_MinoParser thiz, const char* body, libdengyui_KLong vodId, libdengyui_kref_com_dengying_player_core_CmsSource source);
                    libdengyui_kref_com_dengying_player_core_PlayResolveResult (*parsePlay)(libdengyui_kref_com_dengying_player_data_source_adapters_MinoParser thiz, const char* body);
                    libdengyui_KBoolean (*quotaExceeded)(libdengyui_kref_com_dengying_player_data_source_adapters_MinoParser thiz, const char* body);
                    libdengyui_kref_com_dengying_player_core_FetchResult (*search)(libdengyui_kref_com_dengying_player_data_source_adapters_MinoParser thiz, const char* body, libdengyui_kref_com_dengying_player_core_CmsSource source);
                    const char* (*stripHtml)(libdengyui_kref_com_dengying_player_data_source_adapters_MinoParser thiz, const char* value);
                  } MinoParser;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_adapters_Parallel (*_instance)();
                  } Parallel;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_adapters_ShanzhaAdapter (*_instance)();
                  } ShanzhaAdapter;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_adapters_ShanzhaParser (*_instance)();
                    const char* (*encryptedBody)(libdengyui_kref_com_dengying_player_data_source_adapters_ShanzhaParser thiz, const char* plainJson);
                    libdengyui_kref_com_dengying_player_core_FetchResult (*failed)(libdengyui_kref_com_dengying_player_data_source_adapters_ShanzhaParser thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, const char* err, libdengyui_kref_com_dengying_player_core_RequestOutcome outcome);
                    libdengyui_kref_com_dengying_player_core_FetchResult (*parseSearch)(libdengyui_kref_com_dengying_player_data_source_adapters_ShanzhaParser thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, const char* body);
                    const char* (*playUrl)(libdengyui_kref_com_dengying_player_data_source_adapters_ShanzhaParser thiz, const char* body);
                    const char* (*responseKeyPem)(libdengyui_kref_com_dengying_player_data_source_adapters_ShanzhaParser thiz);
                    const char* (*searchBody)(libdengyui_kref_com_dengying_player_data_source_adapters_ShanzhaParser thiz, const char* keyword);
                  } ShanzhaParser;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_adapters_Tv189Adapter (*_instance)();
                    libdengyui_kref_kotlin_collections_List (*classes)(libdengyui_kref_com_dengying_player_data_source_adapters_Tv189Adapter thiz);
                  } Tv189Adapter;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_adapters_Tv189Parser (*_instance)();
                    libdengyui_kref_kotlin_collections_List (*get_CLASSES)(libdengyui_kref_com_dengying_player_data_source_adapters_Tv189Parser thiz);
                    const char* (*get_FREE_PRODUCT_ID)(libdengyui_kref_com_dengying_player_data_source_adapters_Tv189Parser thiz);
                    libdengyui_KLong (*cidToLong)(libdengyui_kref_com_dengying_player_data_source_adapters_Tv189Parser thiz, const char* cid);
                    libdengyui_kref_kotlin_collections_List (*classes)(libdengyui_kref_com_dengying_player_data_source_adapters_Tv189Parser thiz);
                    const char* (*cltKey)(libdengyui_kref_com_dengying_player_data_source_adapters_Tv189Parser thiz, libdengyui_kref_kotlin_collections_Map params, const char* cltts);
                    libdengyui_KInt (*episodeNo)(libdengyui_kref_com_dengying_player_data_source_adapters_Tv189Parser thiz, const char* name);
                    libdengyui_kref_com_dengying_player_core_FetchResult (*failed)(libdengyui_kref_com_dengying_player_data_source_adapters_Tv189Parser thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, libdengyui_KInt page, const char* err);
                    libdengyui_kref_com_dengying_player_core_VideoItem (*parseDetail)(libdengyui_kref_com_dengying_player_data_source_adapters_Tv189Parser thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, const char* body, libdengyui_KLong vodId, const char* vodUri);
                    libdengyui_kref_com_dengying_player_core_FetchResult (*parseList)(libdengyui_kref_com_dengying_player_data_source_adapters_Tv189Parser thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, const char* body, libdengyui_KInt page);
                    libdengyui_kref_com_dengying_player_core_PlayResolveResult (*parsePlay)(libdengyui_kref_com_dengying_player_data_source_adapters_Tv189Parser thiz, const char* body);
                    const char* (*signRaw)(libdengyui_kref_com_dengying_player_data_source_adapters_Tv189Parser thiz, libdengyui_kref_kotlin_collections_Map params, const char* cltts);
                  } Tv189Parser;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_adapters_WebAdapter (*_instance)();
                  } WebAdapter;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_adapters_WebParser (*_instance)();
                    const char* (*absolutize)(libdengyui_kref_com_dengying_player_data_source_adapters_WebParser thiz, const char* base, const char* href);
                    const char* (*dataIdOf)(libdengyui_kref_com_dengying_player_data_source_adapters_WebParser thiz, libdengyui_kref_com_dengying_player_core_WebTokenPlayRule rule, const char* html, const char* pageUrl);
                    libdengyui_kref_kotlin_collections_List (*extractEpisodes)(libdengyui_kref_com_dengying_player_data_source_adapters_WebParser thiz, const char* seg, libdengyui_kref_com_dengying_player_core_WebRule rule, const char* base);
                    libdengyui_kref_com_dengying_player_core_FetchResult (*failed)(libdengyui_kref_com_dengying_player_data_source_adapters_WebParser thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, libdengyui_KInt page, const char* err, libdengyui_kref_com_dengying_player_core_RequestOutcome outcome);
                    libdengyui_KLong (*idOf)(libdengyui_kref_com_dengying_player_data_source_adapters_WebParser thiz, const char* href, libdengyui_kref_com_dengying_player_core_WebIdMode mode);
                    libdengyui_KInt (*lineNoOf)(libdengyui_kref_com_dengying_player_data_source_adapters_WebParser thiz, libdengyui_kref_com_dengying_player_core_WebRule rule);
                    const char* (*originOf)(libdengyui_kref_com_dengying_player_data_source_adapters_WebParser thiz, const char* url);
                    libdengyui_kref_com_dengying_player_core_VideoItem (*parseDetailHtml)(libdengyui_kref_com_dengying_player_data_source_adapters_WebParser thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, const char* html, libdengyui_kref_com_dengying_player_core_WebRule rule, libdengyui_KLong vodId);
                    libdengyui_kref_com_dengying_player_core_FetchResult (*parseSearchHtml)(libdengyui_kref_com_dengying_player_data_source_adapters_WebParser thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, const char* html, libdengyui_kref_com_dengying_player_core_WebRule rule);
                    const char* (*pathOf)(libdengyui_kref_com_dengying_player_data_source_adapters_WebParser thiz, const char* url);
                    libdengyui_kref_com_dengying_player_core_PlayResolveResult (*playFromJsonResponse)(libdengyui_kref_com_dengying_player_data_source_adapters_WebParser thiz, libdengyui_kref_com_dengying_player_core_WebPlayRule rule, const char* body);
                    libdengyui_kref_com_dengying_player_core_PlayResolveResult (*playFromPlayerPage)(libdengyui_kref_com_dengying_player_data_source_adapters_WebParser thiz, libdengyui_kref_com_dengying_player_core_WebPlayRule rule, const char* iframeUrl, const char* playerBody);
                    libdengyui_kref_com_dengying_player_core_PlayResolveResult (*playFromTokenResponse)(libdengyui_kref_com_dengying_player_data_source_adapters_WebParser thiz, libdengyui_kref_com_dengying_player_core_WebTokenPlayRule rule, const char* body);
                    const char* (*playHeaders)(libdengyui_kref_com_dengying_player_data_source_adapters_WebParser thiz, libdengyui_kref_com_dengying_player_core_WebPlayRule rule, const char* iframeUrl);
                    libdengyui_KLong (*slugId)(libdengyui_kref_com_dengying_player_data_source_adapters_WebParser thiz, const char* href);
                    const char* (*slugOf)(libdengyui_kref_com_dengying_player_data_source_adapters_WebParser thiz, const char* url);
                    const char* (*sniffHeaders)(libdengyui_kref_com_dengying_player_data_source_adapters_WebParser thiz, const char* ua, const char* pageUrl);
                    const char* (*sniffMedia)(libdengyui_kref_com_dengying_player_data_source_adapters_WebParser thiz, const char* html, libdengyui_kref_kotlin_collections_List sniff);
                    libdengyui_KLong (*trailingNumber)(libdengyui_kref_com_dengying_player_data_source_adapters_WebParser thiz, const char* href);
                    const char* (*userlinkOf)(libdengyui_kref_com_dengying_player_data_source_adapters_WebParser thiz, libdengyui_kref_com_dengying_player_core_WebTokenPlayRule rule, const char* html);
                  } WebParser;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_adapters_WencaiAdapter (*_instance)();
                  } WencaiAdapter;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_adapters_WencaiParser (*_instance)();
                    libdengyui_KInt (*get_OK_CODE)(libdengyui_kref_com_dengying_player_data_source_adapters_WencaiParser thiz);
                    libdengyui_kref_com_dengying_player_core_FetchResult (*failed)(libdengyui_kref_com_dengying_player_data_source_adapters_WencaiParser thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, libdengyui_KInt page, const char* err, libdengyui_kref_com_dengying_player_core_RequestOutcome outcome);
                    const char* (*nidOf)(libdengyui_kref_com_dengying_player_data_source_adapters_WencaiParser thiz, libdengyui_kref_kotlinx_serialization_json_JsonObject row);
                    const char* (*nidOfToken)(libdengyui_kref_com_dengying_player_data_source_adapters_WencaiParser thiz, const char* token);
                    libdengyui_kref_com_dengying_player_core_VideoItem (*parseDetail)(libdengyui_kref_com_dengying_player_data_source_adapters_WencaiParser thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, const char* body, libdengyui_KLong vodId);
                    libdengyui_kref_com_dengying_player_core_PlayResolveResult (*parsePlay)(libdengyui_kref_com_dengying_player_data_source_adapters_WencaiParser thiz, const char* body);
                    libdengyui_kref_com_dengying_player_core_FetchResult (*parseSearch)(libdengyui_kref_com_dengying_player_data_source_adapters_WencaiParser thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, const char* body);
                    const char* (*stripHtml)(libdengyui_kref_com_dengying_player_data_source_adapters_WencaiParser thiz, const char* value);
                  } WencaiParser;
                } adapters;
                struct {
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_crypto_Aes (*_instance)();
                    libdengyui_kref_kotlin_ByteArray (*cbcDecrypt)(libdengyui_kref_com_dengying_player_data_source_crypto_Aes thiz, const char* alg, libdengyui_kref_kotlin_ByteArray keyBytes, libdengyui_kref_kotlin_ByteArray ivBytes, libdengyui_kref_kotlin_ByteArray cipherBytes);
                    libdengyui_kref_kotlin_ByteArray (*cbcEncrypt)(libdengyui_kref_com_dengying_player_data_source_crypto_Aes thiz, const char* alg, libdengyui_kref_kotlin_ByteArray keyBytes, libdengyui_kref_kotlin_ByteArray ivBytes, libdengyui_kref_kotlin_ByteArray plain);
                    const char* (*cbcHex)(libdengyui_kref_com_dengying_player_data_source_crypto_Aes thiz, const char* alg, const char* keyText, const char* ivText, const char* plainText);
                    libdengyui_kref_kotlin_ByteArray (*ecbDecrypt)(libdengyui_kref_com_dengying_player_data_source_crypto_Aes thiz, const char* alg, libdengyui_kref_kotlin_ByteArray keyBytes, libdengyui_kref_kotlin_ByteArray cipherBytes);
                    libdengyui_kref_kotlin_ByteArray (*ecbEncrypt)(libdengyui_kref_com_dengying_player_data_source_crypto_Aes thiz, const char* alg, libdengyui_kref_kotlin_ByteArray keyBytes, libdengyui_kref_kotlin_ByteArray plain);
                    libdengyui_kref_kotlin_ByteArray (*hexToBytesSafe)(libdengyui_kref_com_dengying_player_data_source_crypto_Aes thiz, const char* hex);
                  } Aes;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_crypto_Digest (*_instance)();
                    libdengyui_kref_kotlin_ByteArray (*base64DecodeBytes)(libdengyui_kref_com_dengying_player_data_source_crypto_Digest thiz, const char* text);
                    const char* (*base64DecodeString)(libdengyui_kref_com_dengying_player_data_source_crypto_Digest thiz, const char* text);
                    const char* (*base64Encode)(libdengyui_kref_com_dengying_player_data_source_crypto_Digest thiz, libdengyui_kref_kotlin_ByteArray bytes);
                    const char* (*base64UrlNoPad)(libdengyui_kref_com_dengying_player_data_source_crypto_Digest thiz, libdengyui_kref_kotlin_ByteArray bytes);
                    const char* (*dubokuUnscramble)(libdengyui_kref_com_dengying_player_data_source_crypto_Digest thiz, const char* encoded);
                    libdengyui_kref_kotlin_ByteArray (*fromHex)(libdengyui_kref_com_dengying_player_data_source_crypto_Digest thiz, const char* hex);
                    const char* (*md5Hex)(libdengyui_kref_com_dengying_player_data_source_crypto_Digest thiz, const char* text);
                    const char* (*sha1Hex)(libdengyui_kref_com_dengying_player_data_source_crypto_Digest thiz, const char* text);
                    const char* (*sha256Hex)(libdengyui_kref_com_dengying_player_data_source_crypto_Digest thiz, const char* text);
                    const char* (*toHex)(libdengyui_kref_com_dengying_player_data_source_crypto_Digest thiz, libdengyui_kref_kotlin_ByteArray bytes);
                    libdengyui_kref_kotlin_ByteArray (*utf8Bytes)(libdengyui_kref_com_dengying_player_data_source_crypto_Digest thiz, const char* text);
                    const char* (*utf8String)(libdengyui_kref_com_dengying_player_data_source_crypto_Digest thiz, libdengyui_kref_kotlin_ByteArray bytes);
                  } Digest;
                  struct {
                    struct {
                      libdengyui_KType* (*_type)(void);
                      libdengyui_kref_com_dengying_player_data_source_crypto_JavaRandom_Companion (*_instance)();
                      const char* (*dubokuRandomString)(libdengyui_kref_com_dengying_player_data_source_crypto_JavaRandom_Companion thiz, libdengyui_KInt len, libdengyui_KLong j, libdengyui_KInt kind, libdengyui_KLong nowSec);
                      const char* (*dubokuSignParam)(libdengyui_kref_com_dengying_player_data_source_crypto_JavaRandom_Companion thiz, libdengyui_KLong nowSec, libdengyui_KLong nowMs);
                    } Companion;
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_crypto_JavaRandom (*JavaRandom)(libdengyui_KLong seed);
                    libdengyui_KLong (*next)(libdengyui_kref_com_dengying_player_data_source_crypto_JavaRandom thiz, libdengyui_KInt bits);
                    libdengyui_KInt (*nextInt)(libdengyui_kref_com_dengying_player_data_source_crypto_JavaRandom thiz, libdengyui_KInt bound);
                  } JavaRandom;
                  struct {
                    struct {
                      libdengyui_KType* (*_type)(void);
                      libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Field (*Field)(libdengyui_KInt fieldNo, libdengyui_KInt wireType, libdengyui_KLong varint, libdengyui_kref_kotlin_ByteArray fixed64, libdengyui_kref_kotlin_ByteArray bytes);
                      libdengyui_kref_kotlin_ByteArray (*get_bytes)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Field thiz);
                      libdengyui_KInt (*get_fieldNo)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Field thiz);
                      libdengyui_kref_kotlin_ByteArray (*get_fixed64)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Field thiz);
                      libdengyui_KLong (*get_varint)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Field thiz);
                      libdengyui_KInt (*get_wireType)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Field thiz);
                      libdengyui_KInt (*component1)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Field thiz);
                      libdengyui_KInt (*component2)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Field thiz);
                      libdengyui_KLong (*component3)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Field thiz);
                      libdengyui_kref_kotlin_ByteArray (*component4)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Field thiz);
                      libdengyui_kref_kotlin_ByteArray (*component5)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Field thiz);
                      libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Field (*copy)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Field thiz, libdengyui_KInt fieldNo, libdengyui_KInt wireType, libdengyui_KLong varint, libdengyui_kref_kotlin_ByteArray fixed64, libdengyui_kref_kotlin_ByteArray bytes);
                      libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Field thiz, libdengyui_kref_kotlin_Any other);
                      libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Field thiz);
                      const char* (*toString)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Field thiz);
                    } Field;
                    struct {
                      libdengyui_KType* (*_type)(void);
                      libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Writer (*Writer)(libdengyui_KInt initialCapacity);
                      libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Writer (*bytesField)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Writer thiz, libdengyui_KInt fieldNo, libdengyui_kref_kotlin_ByteArray data);
                      libdengyui_KInt (*size)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Writer thiz);
                      libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Writer (*stringField)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Writer thiz, libdengyui_KInt fieldNo, const char* text);
                      libdengyui_kref_kotlin_ByteArray (*toBytes)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Writer thiz);
                      libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Writer (*varintField)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf_Writer thiz, libdengyui_KInt fieldNo, libdengyui_KLong value);
                    } Writer;
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf (*_instance)();
                    libdengyui_kref_kotlin_collections_List (*allBytesOf)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf thiz, libdengyui_kref_kotlin_collections_List fields, libdengyui_KInt fieldNo);
                    libdengyui_kref_kotlin_ByteArray (*bytesOf)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf thiz, libdengyui_kref_kotlin_collections_List fields, libdengyui_KInt fieldNo);
                    libdengyui_kref_kotlin_collections_List (*readFields)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf thiz, libdengyui_kref_kotlin_ByteArray bytes);
                    const char* (*textOf)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf thiz, libdengyui_kref_kotlin_collections_List fields, libdengyui_KInt fieldNo);
                    libdengyui_KLong (*varintOf)(libdengyui_kref_com_dengying_player_data_source_crypto_Protobuf thiz, libdengyui_kref_kotlin_collections_List fields, libdengyui_KInt fieldNo);
                  } Protobuf;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_crypto_Rsa (*_instance)();
                    libdengyui_kref_kotlin_ByteArray (*decrypt)(libdengyui_kref_com_dengying_player_data_source_crypto_Rsa thiz, const char* pemOrBase64, libdengyui_kref_kotlin_ByteArray cipherBytes);
                    libdengyui_kref_kotlin_ByteArray (*decryptChunked)(libdengyui_kref_com_dengying_player_data_source_crypto_Rsa thiz, const char* pemOrBase64, libdengyui_kref_kotlin_ByteArray cipherBytes, libdengyui_KInt blockSize);
                    libdengyui_kref_kotlin_ByteArray (*encrypt)(libdengyui_kref_com_dengying_player_data_source_crypto_Rsa thiz, const char* pemOrBase64, libdengyui_kref_kotlin_ByteArray plain);
                    libdengyui_kref_kotlin_ByteArray (*encryptChunked)(libdengyui_kref_com_dengying_player_data_source_crypto_Rsa thiz, const char* pemOrBase64, libdengyui_kref_kotlin_ByteArray plain, libdengyui_KInt chunk);
                    const char* (*encryptToBase64)(libdengyui_kref_com_dengying_player_data_source_crypto_Rsa thiz, const char* pemOrBase64, const char* plainText, libdengyui_KInt chunk);
                    const char* (*wrapPem)(libdengyui_kref_com_dengying_player_data_source_crypto_Rsa thiz, const char* base64Body, const char* kind);
                  } Rsa;
                } crypto;
                struct {
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_discover_DiscoverAdapter (*_instance)();
                  } DiscoverAdapter;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_discover_DiscoverParser (*_instance)();
                    const char* (*get_IQIYI_OK_CODE)(libdengyui_kref_com_dengying_player_data_source_discover_DiscoverParser thiz);
                    libdengyui_kref_com_dengying_player_core_DiscoverFeed (*parseDoubanAnime)(libdengyui_kref_com_dengying_player_data_source_discover_DiscoverParser thiz, const char* body, libdengyui_KInt page, const char* arrayKey);
                    libdengyui_kref_com_dengying_player_core_DiscoverFeed (*parseIqiyiSuggest)(libdengyui_kref_com_dengying_player_data_source_discover_DiscoverParser thiz, const char* body);
                  } DiscoverParser;
                } discover;
                struct {
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_SourceRepository_ClassCacheEntry (*ClassCacheEntry)(libdengyui_kref_kotlin_collections_List classes, libdengyui_KLong time);
                    libdengyui_kref_kotlin_collections_List (*get_classes)(libdengyui_kref_com_dengying_player_data_source_SourceRepository_ClassCacheEntry thiz);
                    libdengyui_KLong (*get_time)(libdengyui_kref_com_dengying_player_data_source_SourceRepository_ClassCacheEntry thiz);
                    libdengyui_kref_kotlin_collections_List (*component1)(libdengyui_kref_com_dengying_player_data_source_SourceRepository_ClassCacheEntry thiz);
                    libdengyui_KLong (*component2)(libdengyui_kref_com_dengying_player_data_source_SourceRepository_ClassCacheEntry thiz);
                    libdengyui_kref_com_dengying_player_data_source_SourceRepository_ClassCacheEntry (*copy)(libdengyui_kref_com_dengying_player_data_source_SourceRepository_ClassCacheEntry thiz, libdengyui_kref_kotlin_collections_List classes, libdengyui_KLong time);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_data_source_SourceRepository_ClassCacheEntry thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_data_source_SourceRepository_ClassCacheEntry thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_data_source_SourceRepository_ClassCacheEntry thiz);
                  } ClassCacheEntry;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_data_source_SourceRepository_AltCandidate (*AltCandidate)(const char* sourceKey, libdengyui_KLong vodId, const char* vodName);
                    const char* (*get_sourceKey)(libdengyui_kref_com_dengying_player_data_source_SourceRepository_AltCandidate thiz);
                    libdengyui_KLong (*get_vodId)(libdengyui_kref_com_dengying_player_data_source_SourceRepository_AltCandidate thiz);
                    const char* (*get_vodName)(libdengyui_kref_com_dengying_player_data_source_SourceRepository_AltCandidate thiz);
                    const char* (*component1)(libdengyui_kref_com_dengying_player_data_source_SourceRepository_AltCandidate thiz);
                    libdengyui_KLong (*component2)(libdengyui_kref_com_dengying_player_data_source_SourceRepository_AltCandidate thiz);
                    const char* (*component3)(libdengyui_kref_com_dengying_player_data_source_SourceRepository_AltCandidate thiz);
                    libdengyui_kref_com_dengying_player_data_source_SourceRepository_AltCandidate (*copy)(libdengyui_kref_com_dengying_player_data_source_SourceRepository_AltCandidate thiz, const char* sourceKey, libdengyui_KLong vodId, const char* vodName);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_data_source_SourceRepository_AltCandidate thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_data_source_SourceRepository_AltCandidate thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_data_source_SourceRepository_AltCandidate thiz);
                  } AltCandidate;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_data_source_SourceRepository (*SourceRepository)();
                  libdengyui_kref_kotlin_coroutines_SuspendFunction0 (*get_classCacheReader)(libdengyui_kref_com_dengying_player_data_source_SourceRepository thiz);
                  void (*set_classCacheReader)(libdengyui_kref_com_dengying_player_data_source_SourceRepository thiz, libdengyui_kref_kotlin_coroutines_SuspendFunction0 set);
                  libdengyui_kref_kotlin_coroutines_SuspendFunction1 (*get_classCacheWriter)(libdengyui_kref_com_dengying_player_data_source_SourceRepository thiz);
                  void (*set_classCacheWriter)(libdengyui_kref_com_dengying_player_data_source_SourceRepository thiz, libdengyui_kref_kotlin_coroutines_SuspendFunction1 set);
                  libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload (*get_override)(libdengyui_kref_com_dengying_player_data_source_SourceRepository thiz);
                  void (*set_override)(libdengyui_kref_com_dengying_player_data_source_SourceRepository thiz, libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload set);
                  libdengyui_kref_kotlin_collections_List (*allSourceHealthRows)(libdengyui_kref_com_dengying_player_data_source_SourceRepository thiz);
                  libdengyui_kref_kotlin_collections_List (*allSources)(libdengyui_kref_com_dengying_player_data_source_SourceRepository thiz);
                  libdengyui_KBoolean (*allSourcesSuspicious)(libdengyui_kref_com_dengying_player_data_source_SourceRepository thiz);
                  libdengyui_kref_kotlin_collections_List (*effectiveSources)(libdengyui_kref_com_dengying_player_data_source_SourceRepository thiz);
                  libdengyui_KInt (*enabledSourceCount)(libdengyui_kref_com_dengying_player_data_source_SourceRepository thiz);
                  libdengyui_KBoolean (*isSourceEnabled)(libdengyui_kref_com_dengying_player_data_source_SourceRepository thiz, libdengyui_kref_com_dengying_player_core_CmsSource s);
                  libdengyui_kref_kotlin_collections_List (*sourceHealthRows)(libdengyui_kref_com_dengying_player_data_source_SourceRepository thiz);
                } SourceRepository;
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_data_source_TypeIdMap (*TypeIdMap)(libdengyui_kref_com_dengying_player_core_CmsSource source, libdengyui_KInt typeId, libdengyui_kref_kotlin_collections_List subTypeIds);
                  libdengyui_kref_com_dengying_player_core_CmsSource (*get_source)(libdengyui_kref_com_dengying_player_data_source_TypeIdMap thiz);
                  libdengyui_kref_kotlin_collections_List (*get_subTypeIds)(libdengyui_kref_com_dengying_player_data_source_TypeIdMap thiz);
                  libdengyui_KInt (*get_typeId)(libdengyui_kref_com_dengying_player_data_source_TypeIdMap thiz);
                  libdengyui_kref_com_dengying_player_core_CmsSource (*component1)(libdengyui_kref_com_dengying_player_data_source_TypeIdMap thiz);
                  libdengyui_KInt (*component2)(libdengyui_kref_com_dengying_player_data_source_TypeIdMap thiz);
                  libdengyui_kref_kotlin_collections_List (*component3)(libdengyui_kref_com_dengying_player_data_source_TypeIdMap thiz);
                  libdengyui_kref_com_dengying_player_data_source_TypeIdMap (*copy)(libdengyui_kref_com_dengying_player_data_source_TypeIdMap thiz, libdengyui_kref_com_dengying_player_core_CmsSource source, libdengyui_KInt typeId, libdengyui_kref_kotlin_collections_List subTypeIds);
                  libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_data_source_TypeIdMap thiz, libdengyui_kref_kotlin_Any other);
                  libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_data_source_TypeIdMap thiz);
                  const char* (*toString)(libdengyui_kref_com_dengying_player_data_source_TypeIdMap thiz);
                } TypeIdMap;
              } source;
              struct {
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_data_store_Store (*Store)(libdengyui_kref_com_dengying_player_platform_KeyValueStore store);
                  libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload (*getSourceOverride)(libdengyui_kref_com_dengying_player_data_store_Store thiz);
                  libdengyui_KBoolean (*hasNewerVersionData)(libdengyui_kref_com_dengying_player_data_store_Store thiz);
                  const char* (*newerVersionNotice)(libdengyui_kref_com_dengying_player_data_store_Store thiz);
                  void (*saveHistoryBlocking)(libdengyui_kref_com_dengying_player_data_store_Store thiz, libdengyui_kref_com_dengying_player_core_HistoryItem item);
                  const char* (*storeFailReason)(libdengyui_kref_com_dengying_player_data_store_Store thiz);
                  libdengyui_kref_com_dengying_player_domain_store_StoreGate_State (*storeState)(libdengyui_kref_com_dengying_player_data_store_Store thiz);
                  libdengyui_KInt (*takeFavoriteDropCount)(libdengyui_kref_com_dengying_player_data_store_Store thiz);
                  const char* (*takeFavoriteDropNotice)(libdengyui_kref_com_dengying_player_data_store_Store thiz);
                } Store;
              } store;
            } data;
            struct {
              struct {
                struct {
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_domain_page_ConcurrencyGate_Companion (*_instance)();
                    libdengyui_KInt (*get_FOREGROUND_RESERVED)(libdengyui_kref_com_dengying_player_domain_page_ConcurrencyGate_Companion thiz);
                    libdengyui_KInt (*get_OUTBOUND_LIMIT)(libdengyui_kref_com_dengying_player_domain_page_ConcurrencyGate_Companion thiz);
                    libdengyui_kref_com_dengying_player_domain_page_ConcurrencyGate (*get_outbound)(libdengyui_kref_com_dengying_player_domain_page_ConcurrencyGate_Companion thiz);
                  } Companion;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_domain_page_ConcurrencyGate (*ConcurrencyGate)(libdengyui_KInt limit, libdengyui_KInt reservedForForeground);
                  libdengyui_KInt (*getBackgroundLimit)(libdengyui_kref_com_dengying_player_domain_page_ConcurrencyGate thiz);
                  libdengyui_KInt (*getLimit)(libdengyui_kref_com_dengying_player_domain_page_ConcurrencyGate thiz);
                } ConcurrencyGate;
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_domain_page_ListBatch (*_instance)();
                  libdengyui_KInt (*get_FIRST_BATCH_MIN)(libdengyui_kref_com_dengying_player_domain_page_ListBatch thiz);
                  libdengyui_KInt (*get_FULL_BATCH_TARGET)(libdengyui_kref_com_dengying_player_domain_page_ListBatch thiz);
                  libdengyui_KInt (*concurrencyFor)(libdengyui_kref_com_dengying_player_domain_page_ListBatch thiz, libdengyui_KBoolean firstEmitted, libdengyui_KInt gateLimit);
                  libdengyui_KBoolean (*shouldEmitFirstBatch)(libdengyui_kref_com_dengying_player_domain_page_ListBatch thiz, libdengyui_KInt mergedCount, libdengyui_KBoolean alreadyEmitted);
                  libdengyui_KBoolean (*shouldStop)(libdengyui_kref_com_dengying_player_domain_page_ListBatch thiz, libdengyui_KInt mergedCount, libdengyui_KInt remainingTasks);
                  libdengyui_KInt (*windowFor)(libdengyui_kref_com_dengying_player_domain_page_ListBatch thiz, libdengyui_KBoolean firstEmitted, libdengyui_KInt gateLimit, libdengyui_KInt remaining);
                } ListBatch;
                struct {
                  struct {
                    struct {
                      libdengyui_kref_com_dengying_player_domain_page_PageBudget_Outcome (*get)(); /* enum entry for Complete. */
                    } Complete;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_page_PageBudget_Outcome (*get)(); /* enum entry for Expired. */
                    } Expired;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_page_PageBudget_Outcome (*get)(); /* enum entry for Cancelled. */
                    } Cancelled;
                    libdengyui_KType* (*_type)(void);
                  } Outcome;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_domain_page_PageBudget (*_instance)();
                  libdengyui_KLong (*get_ALT_SOURCES_BUDGET_MS)(libdengyui_kref_com_dengying_player_domain_page_PageBudget thiz);
                  libdengyui_KLong (*get_CONNECT_TIMEOUT_MS)(libdengyui_kref_com_dengying_player_domain_page_PageBudget thiz);
                  const char* (*get_NOTICE_CANCELLED)(libdengyui_kref_com_dengying_player_domain_page_PageBudget thiz);
                  const char* (*get_NOTICE_INCOMPLETE)(libdengyui_kref_com_dengying_player_domain_page_PageBudget thiz);
                  libdengyui_KLong (*get_PAGE_BUDGET_MS)(libdengyui_kref_com_dengying_player_domain_page_PageBudget thiz);
                  libdengyui_KLong (*get_READ_TIMEOUT_MS)(libdengyui_kref_com_dengying_player_domain_page_PageBudget thiz);
                  libdengyui_KLong (*get_TYPE_RESOLVE_BUDGET_MS)(libdengyui_kref_com_dengying_player_domain_page_PageBudget thiz);
                  libdengyui_KLong (*nextWakeMs)(libdengyui_kref_com_dengying_player_domain_page_PageBudget thiz, libdengyui_KLong nowMs, libdengyui_kref_kotlin_collections_List deadlines);
                  const char* (*outcomeNotice)(libdengyui_kref_com_dengying_player_domain_page_PageBudget thiz, libdengyui_kref_com_dengying_player_domain_page_PageBudget_Outcome kind);
                  libdengyui_KBoolean (*shouldSettle)(libdengyui_kref_com_dengying_player_domain_page_PageBudget thiz, libdengyui_KInt have, libdengyui_KInt need);
                } PageBudget;
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_domain_page_PageClock (*PageClock)(libdengyui_KLong startMs, libdengyui_KLong budgetMs);
                  libdengyui_KLong (*deadlineMs)(libdengyui_kref_com_dengying_player_domain_page_PageClock thiz);
                  libdengyui_KLong (*durationMs)(libdengyui_kref_com_dengying_player_domain_page_PageClock thiz, libdengyui_KLong nowMs);
                  libdengyui_KLong (*elapsed)(libdengyui_kref_com_dengying_player_domain_page_PageClock thiz, libdengyui_KLong nowMs);
                  libdengyui_KBoolean (*expired)(libdengyui_kref_com_dengying_player_domain_page_PageClock thiz, libdengyui_KLong nowMs);
                  libdengyui_KLong (*getBudgetMs)(libdengyui_kref_com_dengying_player_domain_page_PageClock thiz);
                  libdengyui_kref_com_dengying_player_domain_page_PageBudget_Outcome (*getOutcome)(libdengyui_kref_com_dengying_player_domain_page_PageClock thiz);
                  libdengyui_KBoolean (*isComplete)(libdengyui_kref_com_dengying_player_domain_page_PageClock thiz);
                  void (*markOutcome)(libdengyui_kref_com_dengying_player_domain_page_PageClock thiz, libdengyui_kref_com_dengying_player_domain_page_PageBudget_Outcome kind, libdengyui_KLong nowMs);
                  libdengyui_KLong (*remaining)(libdengyui_kref_com_dengying_player_domain_page_PageClock thiz, libdengyui_KLong nowMs);
                } PageClock;
                struct {
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_KLong (*get_accessedAt)(libdengyui_kref_com_dengying_player_domain_page_PageCacheRules_Entry thiz);
                    const char* (*get_key)(libdengyui_kref_com_dengying_player_domain_page_PageCacheRules_Entry thiz);
                    libdengyui_KLong (*get_savedAt)(libdengyui_kref_com_dengying_player_domain_page_PageCacheRules_Entry thiz);
                  } Entry;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_domain_page_PageCacheRules (*_instance)();
                  libdengyui_KInt (*get_MAX_ENTRIES)(libdengyui_kref_com_dengying_player_domain_page_PageCacheRules thiz);
                  libdengyui_KLong (*get_RETAIN_MS)(libdengyui_kref_com_dengying_player_domain_page_PageCacheRules thiz);
                  libdengyui_KLong (*get_STALE_MS)(libdengyui_kref_com_dengying_player_domain_page_PageCacheRules thiz);
                  const char* (*categoryOf)(libdengyui_kref_com_dengying_player_domain_page_PageCacheRules thiz, const char* key);
                  libdengyui_kref_kotlin_collections_List (*evictOrder)(libdengyui_kref_com_dengying_player_domain_page_PageCacheRules thiz, libdengyui_kref_kotlin_collections_List entries, const char* incomingKey, libdengyui_KLong nowMs, libdengyui_KInt max);
                  libdengyui_kref_kotlin_collections_List (*expiredKeys)(libdengyui_kref_com_dengying_player_domain_page_PageCacheRules thiz, libdengyui_kref_kotlin_collections_List entries, libdengyui_KLong nowMs);
                  libdengyui_kref_kotlin_collections_List (*invalidateOnSourceChange)(libdengyui_kref_com_dengying_player_domain_page_PageCacheRules thiz, libdengyui_kref_kotlin_collections_List entries);
                  libdengyui_KBoolean (*isStale)(libdengyui_kref_com_dengying_player_domain_page_PageCacheRules thiz, libdengyui_kref_com_dengying_player_domain_page_PageCacheRules_Entry entry, libdengyui_KLong nowMs);
                  const char* (*keyOf)(libdengyui_kref_com_dengying_player_domain_page_PageCacheRules thiz, const char* category, libdengyui_KInt page);
                  libdengyui_KInt (*pageOf)(libdengyui_kref_com_dengying_player_domain_page_PageCacheRules thiz, const char* key);
                  libdengyui_KBoolean (*shouldServe)(libdengyui_kref_com_dengying_player_domain_page_PageCacheRules thiz, libdengyui_kref_com_dengying_player_domain_page_PageCacheRules_Entry entry, libdengyui_KLong nowMs);
                  libdengyui_kref_kotlin_collections_List (*staleKeys)(libdengyui_kref_com_dengying_player_domain_page_PageCacheRules thiz, libdengyui_kref_kotlin_collections_List entries, libdengyui_KLong nowMs);
                } PageCacheRules;
              } page;
              struct {
                struct {
                  struct {
                    struct {
                      libdengyui_kref_com_dengying_player_domain_playback_CastUtil_CastabilityKind (*get)(); /* enum entry for Castable. */
                    } Castable;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_playback_CastUtil_CastabilityKind (*get)(); /* enum entry for NotCastable. */
                    } NotCastable;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_playback_CastUtil_CastabilityKind (*get)(); /* enum entry for Trial. */
                    } Trial;
                    libdengyui_KType* (*_type)(void);
                  } CastabilityKind;
                  struct {
                    struct {
                      libdengyui_kref_com_dengying_player_domain_playback_CastUtil_BlockReason (*get)(); /* enum entry for None. */
                    } None;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_playback_CastUtil_BlockReason (*get)(); /* enum entry for NeedsHeaders. */
                    } NeedsHeaders;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_playback_CastUtil_BlockReason (*get)(); /* enum entry for AddressNotReady. */
                    } AddressNotReady;
                    libdengyui_KType* (*_type)(void);
                  } BlockReason;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_domain_playback_CastUtil_Castability (*Castability)(libdengyui_kref_com_dengying_player_domain_playback_CastUtil_CastabilityKind kind, libdengyui_kref_com_dengying_player_domain_playback_CastUtil_BlockReason reason);
                    libdengyui_kref_com_dengying_player_domain_playback_CastUtil_CastabilityKind (*get_kind)(libdengyui_kref_com_dengying_player_domain_playback_CastUtil_Castability thiz);
                    libdengyui_kref_com_dengying_player_domain_playback_CastUtil_BlockReason (*get_reason)(libdengyui_kref_com_dengying_player_domain_playback_CastUtil_Castability thiz);
                    libdengyui_kref_com_dengying_player_domain_playback_CastUtil_CastabilityKind (*component1)(libdengyui_kref_com_dengying_player_domain_playback_CastUtil_Castability thiz);
                    libdengyui_kref_com_dengying_player_domain_playback_CastUtil_BlockReason (*component2)(libdengyui_kref_com_dengying_player_domain_playback_CastUtil_Castability thiz);
                    libdengyui_kref_com_dengying_player_domain_playback_CastUtil_Castability (*copy)(libdengyui_kref_com_dengying_player_domain_playback_CastUtil_Castability thiz, libdengyui_kref_com_dengying_player_domain_playback_CastUtil_CastabilityKind kind, libdengyui_kref_com_dengying_player_domain_playback_CastUtil_BlockReason reason);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_domain_playback_CastUtil_Castability thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_domain_playback_CastUtil_Castability thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_domain_playback_CastUtil_Castability thiz);
                  } Castability;
                  struct {
                    struct {
                      libdengyui_kref_com_dengying_player_domain_playback_CastUtil_FailureKind (*get)(); /* enum entry for DeviceUnreachable. */
                    } DeviceUnreachable;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_playback_CastUtil_FailureKind (*get)(); /* enum entry for RemotePullFail. */
                    } RemotePullFail;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_playback_CastUtil_FailureKind (*get)(); /* enum entry for AddressGone. */
                    } AddressGone;
                    libdengyui_KType* (*_type)(void);
                  } FailureKind;
                  struct {
                    struct {
                      libdengyui_kref_com_dengying_player_domain_playback_CastUtil_Stage (*get)(); /* enum entry for Precheck. */
                    } Precheck;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_playback_CastUtil_Stage (*get)(); /* enum entry for Link. */
                    } Link;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_playback_CastUtil_Stage (*get)(); /* enum entry for Handoff. */
                    } Handoff;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_playback_CastUtil_Stage (*get)(); /* enum entry for Remote. */
                    } Remote;
                    libdengyui_KType* (*_type)(void);
                  } Stage;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_domain_playback_CastUtil (*_instance)();
                  const char* (*castBlockText)(libdengyui_kref_com_dengying_player_domain_playback_CastUtil thiz, libdengyui_kref_com_dengying_player_domain_playback_CastUtil_BlockReason reason);
                  const char* (*castFailureText)(libdengyui_kref_com_dengying_player_domain_playback_CastUtil thiz, libdengyui_kref_com_dengying_player_domain_playback_CastUtil_FailureKind kind);
                  libdengyui_kref_com_dengying_player_domain_playback_CastUtil_FailureKind (*classifyCastFailure)(libdengyui_kref_com_dengying_player_domain_playback_CastUtil thiz, libdengyui_kref_com_dengying_player_domain_playback_CastUtil_Stage stage, libdengyui_KBoolean hasAddress);
                  libdengyui_kref_com_dengying_player_domain_playback_CastUtil_Castability (*decideCastability)(libdengyui_kref_com_dengying_player_domain_playback_CastUtil thiz, const char* mediaUrl, libdengyui_KBoolean requiresHeaders, libdengyui_KBoolean fromSourceChain);
                  libdengyui_KBoolean (*isCastEntryEnabled)(libdengyui_kref_com_dengying_player_domain_playback_CastUtil thiz, libdengyui_kref_com_dengying_player_domain_playback_CastUtil_Castability c);
                  libdengyui_KBoolean (*isCastableUrl)(libdengyui_kref_com_dengying_player_domain_playback_CastUtil thiz, const char* url);
                } CastUtil;
                struct {
                  struct {
                    struct {
                      libdengyui_kref_com_dengying_player_domain_playback_GestureRules_Action (*get)(); /* enum entry for Undecided. */
                    } Undecided;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_playback_GestureRules_Action (*get)(); /* enum entry for Seek. */
                    } Seek;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_playback_GestureRules_Action (*get)(); /* enum entry for Brightness. */
                    } Brightness;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_playback_GestureRules_Action (*get)(); /* enum entry for Volume. */
                    } Volume;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_playback_GestureRules_Action (*get)(); /* enum entry for Discard. */
                    } Discard;
                    libdengyui_KType* (*_type)(void);
                  } Action;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_domain_playback_GestureRules (*_instance)();
                  libdengyui_KFloat (*get_THRESHOLD_PX)(libdengyui_kref_com_dengying_player_domain_playback_GestureRules thiz);
                  libdengyui_kref_com_dengying_player_domain_playback_GestureRules_Action (*decide)(libdengyui_kref_com_dengying_player_domain_playback_GestureRules thiz, libdengyui_KFloat totalX, libdengyui_KFloat totalY, libdengyui_KFloat startX, libdengyui_KFloat viewWidth, libdengyui_KBoolean brightnessAvailable, libdengyui_KFloat threshold);
                } GestureRules;
                struct {
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_domain_playback_HlsVariants_Variant (*Variant)(libdengyui_KInt bandwidth, const char* resolution, libdengyui_KInt height, const char* url);
                    libdengyui_KInt (*get_bandwidth)(libdengyui_kref_com_dengying_player_domain_playback_HlsVariants_Variant thiz);
                    libdengyui_KInt (*get_height)(libdengyui_kref_com_dengying_player_domain_playback_HlsVariants_Variant thiz);
                    const char* (*get_resolution)(libdengyui_kref_com_dengying_player_domain_playback_HlsVariants_Variant thiz);
                    const char* (*get_url)(libdengyui_kref_com_dengying_player_domain_playback_HlsVariants_Variant thiz);
                    libdengyui_KInt (*component1)(libdengyui_kref_com_dengying_player_domain_playback_HlsVariants_Variant thiz);
                    const char* (*component2)(libdengyui_kref_com_dengying_player_domain_playback_HlsVariants_Variant thiz);
                    libdengyui_KInt (*component3)(libdengyui_kref_com_dengying_player_domain_playback_HlsVariants_Variant thiz);
                    const char* (*component4)(libdengyui_kref_com_dengying_player_domain_playback_HlsVariants_Variant thiz);
                    libdengyui_kref_com_dengying_player_domain_playback_HlsVariants_Variant (*copy)(libdengyui_kref_com_dengying_player_domain_playback_HlsVariants_Variant thiz, libdengyui_KInt bandwidth, const char* resolution, libdengyui_KInt height, const char* url);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_domain_playback_HlsVariants_Variant thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_domain_playback_HlsVariants_Variant thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_domain_playback_HlsVariants_Variant thiz);
                  } Variant;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_domain_playback_HlsVariants (*_instance)();
                  libdengyui_KBoolean (*isStale)(libdengyui_kref_com_dengying_player_domain_playback_HlsVariants thiz, const char* requestUrl, const char* currentUrl);
                  libdengyui_kref_kotlin_collections_List (*parseVariants)(libdengyui_kref_com_dengying_player_domain_playback_HlsVariants thiz, const char* text, const char* baseUrl);
                  libdengyui_kref_com_dengying_player_domain_playback_HlsVariants_Variant (*pickByUrl)(libdengyui_kref_com_dengying_player_domain_playback_HlsVariants thiz, libdengyui_kref_kotlin_collections_List variants, const char* wantUrl);
                  libdengyui_kref_com_dengying_player_domain_playback_HlsVariants_Variant (*pickDefault)(libdengyui_kref_com_dengying_player_domain_playback_HlsVariants thiz, libdengyui_kref_kotlin_collections_List variants);
                  const char* (*resolveVariantUrl)(libdengyui_kref_com_dengying_player_domain_playback_HlsVariants thiz, const char* base, const char* raw);
                  libdengyui_KBoolean (*shouldShowPicker)(libdengyui_kref_com_dengying_player_domain_playback_HlsVariants thiz, libdengyui_kref_kotlin_collections_List variants);
                  libdengyui_kref_kotlin_collections_List (*sortVariants)(libdengyui_kref_com_dengying_player_domain_playback_HlsVariants thiz, libdengyui_kref_kotlin_collections_List list);
                  const char* (*variantLabel)(libdengyui_kref_com_dengying_player_domain_playback_HlsVariants thiz, libdengyui_kref_com_dengying_player_domain_playback_HlsVariants_Variant v);
                  libdengyui_KBoolean (*wantsVariants)(libdengyui_kref_com_dengying_player_domain_playback_HlsVariants thiz, const char* url, libdengyui_KBoolean fromSource);
                } HlsVariants;
                struct {
                  struct {
                    struct {
                      libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Verdict (*get)(); /* enum entry for Play. */
                    } Play;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Verdict (*get)(); /* enum entry for StayNoLines. */
                    } StayNoLines;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Verdict (*get)(); /* enum entry for StayEpMissing. */
                    } StayEpMissing;
                    libdengyui_KType* (*_type)(void);
                  } Verdict;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Decision (*Decision)(libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Verdict verdict, libdengyui_KInt episodeIndex, const char* notice);
                    libdengyui_KInt (*get_episodeIndex)(libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Decision thiz);
                    const char* (*get_notice)(libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Decision thiz);
                    libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Verdict (*get_verdict)(libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Decision thiz);
                    libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Verdict (*component1)(libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Decision thiz);
                    libdengyui_KInt (*component2)(libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Decision thiz);
                    const char* (*component3)(libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Decision thiz);
                    libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Decision (*copy)(libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Decision thiz, libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Verdict verdict, libdengyui_KInt episodeIndex, const char* notice);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Decision thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Decision thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Decision thiz);
                  } Decision;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide (*_instance)();
                  const char* (*get_NOTICE_NEED_OTHER_LINE)(libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide thiz);
                  const char* (*get_NOTICE_NO_LINE)(libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide thiz);
                  libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide_Decision (*decideResume)(libdengyui_kref_com_dengying_player_domain_playback_ResumeDecide thiz, libdengyui_KBoolean autoPlay, libdengyui_KInt episodeCount, libdengyui_KInt resumeEpisode);
                } ResumeDecide;
                struct {
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Load (*Load)(libdengyui_KBoolean shouldLoad, const char* reason);
                    const char* (*get_reason)(libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Load thiz);
                    libdengyui_KBoolean (*get_shouldLoad)(libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Load thiz);
                    libdengyui_KBoolean (*component1)(libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Load thiz);
                    const char* (*component2)(libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Load thiz);
                    libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Load (*copy)(libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Load thiz, libdengyui_KBoolean shouldLoad, const char* reason);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Load thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Load thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Load thiz);
                  } Load;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Apply (*Apply)(libdengyui_KLong targetMs, libdengyui_KBoolean shouldSeek, const char* reason);
                    const char* (*get_reason)(libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Apply thiz);
                    libdengyui_KBoolean (*get_shouldSeek)(libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Apply thiz);
                    libdengyui_KLong (*get_targetMs)(libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Apply thiz);
                    libdengyui_KLong (*component1)(libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Apply thiz);
                    libdengyui_KBoolean (*component2)(libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Apply thiz);
                    const char* (*component3)(libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Apply thiz);
                    libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Apply (*copy)(libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Apply thiz, libdengyui_KLong targetMs, libdengyui_KBoolean shouldSeek, const char* reason);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Apply thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Apply thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Apply thiz);
                  } Apply;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore (*_instance)();
                  libdengyui_KLong (*get_HEAD_GUARD_MS)(libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore thiz);
                  libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Apply (*apply)(libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore thiz, libdengyui_KLong storedMs, libdengyui_KBoolean manualSeekOccurred, libdengyui_KLong headGuardMs);
                  libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore_Load (*shouldLoad)(libdengyui_kref_com_dengying_player_domain_playback_ResumeRestore thiz, libdengyui_KLong startAtMs, libdengyui_KLong vodId, const char* source, libdengyui_KBoolean alreadyRequested);
                } ResumeRestore;
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_domain_playback_SeekPreview (*_instance)();
                  libdengyui_KLong (*get_ADOPT_TOLERANCE_MS)(libdengyui_kref_com_dengying_player_domain_playback_SeekPreview thiz);
                  const char* (*deltaLabel)(libdengyui_kref_com_dengying_player_domain_playback_SeekPreview thiz, libdengyui_KLong fromMs, libdengyui_KLong targetMs);
                  libdengyui_KBoolean (*isForward)(libdengyui_kref_com_dengying_player_domain_playback_SeekPreview thiz, libdengyui_KLong fromMs, libdengyui_KLong targetMs);
                  libdengyui_KBoolean (*shouldAdoptEnginePosition)(libdengyui_kref_com_dengying_player_domain_playback_SeekPreview thiz, libdengyui_KLong engineMs, libdengyui_KLong pendingTargetMs, libdengyui_KLong toleranceMs);
                  libdengyui_KLong (*targetOf)(libdengyui_kref_com_dengying_player_domain_playback_SeekPreview thiz, libdengyui_KLong fromMs, libdengyui_KFloat deltaX, libdengyui_KFloat viewWidth, libdengyui_KLong totalMs);
                } SeekPreview;
              } playback;
              struct {
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_domain_search_DiscoverRules (*_instance)();
                  libdengyui_kref_kotlin_collections_List (*rankingItems)(libdengyui_kref_com_dengying_player_domain_search_DiscoverRules thiz, libdengyui_kref_com_dengying_player_core_DiscoverFeed feed);
                  libdengyui_kref_kotlin_collections_List (*suggestItems)(libdengyui_kref_com_dengying_player_domain_search_DiscoverRules thiz, libdengyui_kref_com_dengying_player_core_DiscoverFeed feed);
                } DiscoverRules;
                struct {
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_domain_search_SearchNotice_SearchableSource (*SearchableSource)(libdengyui_KBoolean enabled, libdengyui_KBoolean capsSearch);
                    libdengyui_KBoolean (*get_capsSearch)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice_SearchableSource thiz);
                    libdengyui_KBoolean (*get_enabled)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice_SearchableSource thiz);
                    libdengyui_KBoolean (*component1)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice_SearchableSource thiz);
                    libdengyui_KBoolean (*component2)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice_SearchableSource thiz);
                    libdengyui_kref_com_dengying_player_domain_search_SearchNotice_SearchableSource (*copy)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice_SearchableSource thiz, libdengyui_KBoolean enabled, libdengyui_KBoolean capsSearch);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice_SearchableSource thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice_SearchableSource thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice_SearchableSource thiz);
                  } SearchableSource;
                  struct {
                    struct {
                      libdengyui_kref_com_dengying_player_domain_search_SearchNotice_EpisodeKind (*get)(); /* enum entry for Ok. */
                    } Ok;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_search_SearchNotice_EpisodeKind (*get)(); /* enum entry for NoLines. */
                    } NoLines;
                    libdengyui_KType* (*_type)(void);
                  } EpisodeKind;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_domain_search_SearchNotice_EpisodeState (*EpisodeState)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice_EpisodeKind kind, const char* notice, libdengyui_KBoolean showGrid);
                    libdengyui_kref_com_dengying_player_domain_search_SearchNotice_EpisodeKind (*get_kind)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice_EpisodeState thiz);
                    const char* (*get_notice)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice_EpisodeState thiz);
                    libdengyui_KBoolean (*get_showGrid)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice_EpisodeState thiz);
                    libdengyui_kref_com_dengying_player_domain_search_SearchNotice_EpisodeKind (*component1)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice_EpisodeState thiz);
                    const char* (*component2)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice_EpisodeState thiz);
                    libdengyui_KBoolean (*component3)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice_EpisodeState thiz);
                    libdengyui_kref_com_dengying_player_domain_search_SearchNotice_EpisodeState (*copy)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice_EpisodeState thiz, libdengyui_kref_com_dengying_player_domain_search_SearchNotice_EpisodeKind kind, const char* notice, libdengyui_KBoolean showGrid);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice_EpisodeState thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice_EpisodeState thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice_EpisodeState thiz);
                  } EpisodeState;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_domain_search_SearchNotice (*_instance)();
                  const char* (*get_NOTICE_NO_LINES)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice thiz);
                  libdengyui_KInt (*countSearchable)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice thiz, libdengyui_kref_kotlin_collections_List sources);
                  libdengyui_kref_com_dengying_player_domain_search_SearchNotice_EpisodeState (*decideEpisodeState)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice thiz, libdengyui_KInt lineCount, libdengyui_KInt episodeCount);
                  const char* (*doneText)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice thiz, libdengyui_KInt found, const char* keyword, libdengyui_KInt sourceCount);
                  const char* (*partialText)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice thiz, libdengyui_KInt found, libdengyui_KInt sourceCount);
                  const char* (*searchingText)(libdengyui_kref_com_dengying_player_domain_search_SearchNotice thiz, libdengyui_KInt sourceCount);
                } SearchNotice;
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_domain_search_SearchSession (*SearchSession)();
                  libdengyui_KInt (*begin)(libdengyui_kref_com_dengying_player_domain_search_SearchSession thiz, const char* keyword);
                  libdengyui_KInt (*current)(libdengyui_kref_com_dengying_player_domain_search_SearchSession thiz);
                  libdengyui_KBoolean (*finish)(libdengyui_kref_com_dengying_player_domain_search_SearchSession thiz, libdengyui_KInt token);
                  libdengyui_KBoolean (*isCurrent)(libdengyui_kref_com_dengying_player_domain_search_SearchSession thiz, libdengyui_KInt token);
                  libdengyui_KBoolean (*isRunning)(libdengyui_kref_com_dengying_player_domain_search_SearchSession thiz);
                  const char* (*keyword)(libdengyui_kref_com_dengying_player_domain_search_SearchSession thiz);
                  libdengyui_KBoolean (*shouldAdopt)(libdengyui_kref_com_dengying_player_domain_search_SearchSession thiz, libdengyui_KInt token, const char* keyword);
                  libdengyui_KBoolean (*shouldSkip)(libdengyui_kref_com_dengying_player_domain_search_SearchSession thiz, const char* keyword, libdengyui_KBoolean hasResults);
                } SearchSession;
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_domain_search_TitleKey (*_instance)();
                  const char* (*normalizeTitle)(libdengyui_kref_com_dengying_player_domain_search_TitleKey thiz, const char* title);
                  const char* (*of)(libdengyui_kref_com_dengying_player_domain_search_TitleKey thiz, const char* title, libdengyui_kref_kotlin_Int year);
                  const char* (*ofRaw)(libdengyui_kref_com_dengying_player_domain_search_TitleKey thiz, const char* title, const char* yearText);
                  libdengyui_KInt (*parseYear)(libdengyui_kref_com_dengying_player_domain_search_TitleKey thiz, const char* yearText);
                  const char* (*toHalfWidth)(libdengyui_kref_com_dengying_player_domain_search_TitleKey thiz, const char* s);
                } TitleKey;
              } search;
              struct {
                struct {
                  struct {
                    struct {
                      libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Outcome (*get)(); /* enum entry for Ok. */
                    } Ok;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Outcome (*get)(); /* enum entry for SuccessNoData. */
                    } SuccessNoData;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Outcome (*get)(); /* enum entry for Failed. */
                    } Failed;
                    libdengyui_KType* (*_type)(void);
                  } Outcome;
                  struct {
                    struct {
                      libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_State (*get)(); /* enum entry for Active. */
                    } Active;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_State (*get)(); /* enum entry for Suspicious. */
                    } Suspicious;
                    libdengyui_KType* (*_type)(void);
                  } State;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Health (*Health)(const char* key, libdengyui_KInt score, libdengyui_KInt samples, libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Outcome lastOutcome);
                    const char* (*get_key)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Health thiz);
                    libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Outcome (*get_lastOutcome)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Health thiz);
                    libdengyui_KInt (*get_samples)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Health thiz);
                    libdengyui_KInt (*get_score)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Health thiz);
                    const char* (*component1)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Health thiz);
                    libdengyui_KInt (*component2)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Health thiz);
                    libdengyui_KInt (*component3)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Health thiz);
                    libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Outcome (*component4)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Health thiz);
                    libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Health (*copy)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Health thiz, const char* key, libdengyui_KInt score, libdengyui_KInt samples, libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Outcome lastOutcome);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Health thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Health thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Health thiz);
                  } Health;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row (*Row)(const char* key, libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_State state, libdengyui_KInt score, libdengyui_KInt samples, libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Outcome lastOutcome, const char* note);
                    const char* (*get_key)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row thiz);
                    libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Outcome (*get_lastOutcome)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row thiz);
                    const char* (*get_note)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row thiz);
                    libdengyui_KInt (*get_samples)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row thiz);
                    libdengyui_KInt (*get_score)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row thiz);
                    libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_State (*get_state)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row thiz);
                    const char* (*component1)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row thiz);
                    libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_State (*component2)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row thiz);
                    libdengyui_KInt (*component3)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row thiz);
                    libdengyui_KInt (*component4)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row thiz);
                    libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Outcome (*component5)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row thiz);
                    const char* (*component6)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row thiz);
                    libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row (*copy)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row thiz, const char* key, libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_State state, libdengyui_KInt score, libdengyui_KInt samples, libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Outcome lastOutcome, const char* note);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row thiz);
                  } Row;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules (*_instance)();
                  libdengyui_KInt (*get_HEALTH_WINDOW)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules thiz);
                  libdengyui_KBoolean (*needFallbackAll)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules thiz, libdengyui_kref_kotlin_collections_List healths);
                  libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Health (*newHealth)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules thiz, const char* key);
                  libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Health (*record)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules thiz, libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Health h, libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Outcome outcome);
                  libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_State (*stateOf)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules thiz, libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Health h);
                  libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row (*toRow)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules thiz, libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Health h);
                  libdengyui_kref_kotlin_collections_List (*toRows)(libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules thiz, libdengyui_kref_kotlin_collections_List healths);
                } SourceHealthRules;
                struct {
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Override (*Override)(const char* key, libdengyui_KBoolean enabled, libdengyui_KLong updatedAt);
                    libdengyui_KBoolean (*get_enabled)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Override thiz);
                    const char* (*get_key)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Override thiz);
                    libdengyui_KLong (*get_updatedAt)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Override thiz);
                    const char* (*component1)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Override thiz);
                    libdengyui_KBoolean (*component2)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Override thiz);
                    libdengyui_KLong (*component3)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Override thiz);
                    libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Override (*copy)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Override thiz, const char* key, libdengyui_KBoolean enabled, libdengyui_KLong updatedAt);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Override thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Override thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Override thiz);
                  } Override;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload (*Payload)(libdengyui_KInt version, libdengyui_kref_kotlin_collections_List list);
                    libdengyui_kref_kotlin_collections_List (*get_list)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload thiz);
                    libdengyui_KInt (*get_version)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload thiz);
                    libdengyui_KInt (*component1)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload thiz);
                    libdengyui_kref_kotlin_collections_List (*component2)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload thiz);
                    libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload (*copy)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload thiz, libdengyui_KInt version, libdengyui_kref_kotlin_collections_List list);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload thiz);
                  } Payload;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Baseline (*Baseline)(const char* key, libdengyui_KBoolean enabled);
                    libdengyui_KBoolean (*get_enabled)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Baseline thiz);
                    const char* (*get_key)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Baseline thiz);
                    const char* (*component1)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Baseline thiz);
                    libdengyui_KBoolean (*component2)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Baseline thiz);
                    libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Baseline (*copy)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Baseline thiz, const char* key, libdengyui_KBoolean enabled);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Baseline thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Baseline thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Baseline thiz);
                  } Baseline;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules (*_instance)();
                  libdengyui_KInt (*get_OVERRIDE_VERSION)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules thiz);
                  libdengyui_KInt (*changedCount)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules thiz, libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload payload);
                  libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload (*clearAll)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules thiz, libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload payload);
                  libdengyui_KInt (*effectiveEnabledCount)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules thiz, libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload payload, libdengyui_kref_kotlin_collections_List baselines);
                  libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload (*emptyPayload)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules thiz);
                  libdengyui_KBoolean (*enabledOf)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules thiz, libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload payload, const char* key, libdengyui_KBoolean baselineEnabled);
                  libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Override (*overrideOf)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules thiz, libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload payload, const char* key);
                  libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload (*setEnabled)(libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules thiz, libdengyui_kref_com_dengying_player_domain_source_SourceOverrideRules_Payload payload, const char* key, libdengyui_KBoolean enabled, libdengyui_KBoolean baselineEnabled, libdengyui_KLong nowMs);
                } SourceOverrideRules;
              } source;
              struct {
                struct {
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_KLong (*get_addTime)(libdengyui_kref_com_dengying_player_domain_store_FavoritePrune_Entry thiz);
                    const char* (*get_source)(libdengyui_kref_com_dengying_player_domain_store_FavoritePrune_Entry thiz);
                    libdengyui_KLong (*get_vodId)(libdengyui_kref_com_dengying_player_domain_store_FavoritePrune_Entry thiz);
                  } Entry;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_domain_store_FavoritePrune (*_instance)();
                  libdengyui_KInt (*get_MAX_FAVORITES)(libdengyui_kref_com_dengying_player_domain_store_FavoritePrune thiz);
                  const char* (*favoriteKeyOf)(libdengyui_kref_com_dengying_player_domain_store_FavoritePrune thiz, libdengyui_kref_com_dengying_player_domain_store_FavoritePrune_Entry e);
                  const char* (*prunedNotice)(libdengyui_kref_com_dengying_player_domain_store_FavoritePrune thiz, libdengyui_KInt dropped);
                } FavoritePrune;
                struct {
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_KInt (*get_episodeIndex)(libdengyui_kref_com_dengying_player_domain_store_LedgerPrune_Entry thiz);
                    const char* (*get_source)(libdengyui_kref_com_dengying_player_domain_store_LedgerPrune_Entry thiz);
                    libdengyui_KLong (*get_updateTime)(libdengyui_kref_com_dengying_player_domain_store_LedgerPrune_Entry thiz);
                    libdengyui_KLong (*get_vodId)(libdengyui_kref_com_dengying_player_domain_store_LedgerPrune_Entry thiz);
                  } Entry;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_domain_store_LedgerPrune (*_instance)();
                  libdengyui_KInt (*groupCountOf)(libdengyui_kref_com_dengying_player_domain_store_LedgerPrune thiz, libdengyui_kref_kotlin_collections_List list);
                  const char* (*groupKeyOf)(libdengyui_kref_com_dengying_player_domain_store_LedgerPrune thiz, libdengyui_kref_com_dengying_player_domain_store_LedgerPrune_Entry e);
                } LedgerPrune;
                struct {
                  struct {
                    struct {
                      libdengyui_kref_com_dengying_player_domain_store_StoreGate_State (*get)(); /* enum entry for Pending. */
                    } Pending;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_store_StoreGate_State (*get)(); /* enum entry for Ready. */
                    } Ready;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_store_StoreGate_State (*get)(); /* enum entry for Failed. */
                    } Failed;
                    libdengyui_KType* (*_type)(void);
                  } State;
                  struct {
                    struct {
                      libdengyui_kref_com_dengying_player_domain_store_StoreGate_Verdict (*get)(); /* enum entry for Proceed. */
                    } Proceed;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_store_StoreGate_Verdict (*get)(); /* enum entry for Wait. */
                    } Wait;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_store_StoreGate_Verdict (*get)(); /* enum entry for Reject. */
                    } Reject;
                    libdengyui_KType* (*_type)(void);
                  } Verdict;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_domain_store_StoreGate (*StoreGate)();
                  libdengyui_kref_kotlinx_coroutines_CoroutineScope (*get_scope)(libdengyui_kref_com_dengying_player_domain_store_StoreGate thiz);
                  void (*set_scope)(libdengyui_kref_com_dengying_player_domain_store_StoreGate thiz, libdengyui_kref_kotlinx_coroutines_CoroutineScope set);
                  void (*bind)(libdengyui_kref_com_dengying_player_domain_store_StoreGate thiz, libdengyui_kref_kotlin_coroutines_SuspendFunction0 opener);
                  libdengyui_KBoolean (*isInflight)(libdengyui_kref_com_dengying_player_domain_store_StoreGate thiz);
                  libdengyui_KBoolean (*isReady)(libdengyui_kref_com_dengying_player_domain_store_StoreGate thiz);
                  const char* (*reason)(libdengyui_kref_com_dengying_player_domain_store_StoreGate thiz);
                  void (*settleWith)(libdengyui_kref_com_dengying_player_domain_store_StoreGate thiz, libdengyui_KBoolean ok, const char* reason);
                  libdengyui_KBoolean (*shouldRetry)(libdengyui_kref_com_dengying_player_domain_store_StoreGate thiz);
                  libdengyui_kref_com_dengying_player_domain_store_StoreGate_State (*state)(libdengyui_kref_com_dengying_player_domain_store_StoreGate thiz);
                  libdengyui_kref_com_dengying_player_domain_store_StoreGate_Verdict (*verdict)(libdengyui_kref_com_dengying_player_domain_store_StoreGate thiz);
                } StoreGate;
              } store;
              struct {
                struct {
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec_Spec (*Spec)(const char* title, const char* message, const char* confirmText, const char* cancelText, libdengyui_KBoolean focusCancel);
                    const char* (*get_cancelText)(libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec_Spec thiz);
                    const char* (*get_confirmText)(libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec_Spec thiz);
                    libdengyui_KBoolean (*get_focusCancel)(libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec_Spec thiz);
                    const char* (*get_message)(libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec_Spec thiz);
                    const char* (*get_title)(libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec_Spec thiz);
                    const char* (*component1)(libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec_Spec thiz);
                    const char* (*component2)(libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec_Spec thiz);
                    const char* (*component3)(libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec_Spec thiz);
                    const char* (*component4)(libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec_Spec thiz);
                    libdengyui_KBoolean (*component5)(libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec_Spec thiz);
                    libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec_Spec (*copy)(libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec_Spec thiz, const char* title, const char* message, const char* confirmText, const char* cancelText, libdengyui_KBoolean focusCancel);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec_Spec thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec_Spec thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec_Spec thiz);
                  } Spec;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec (*_instance)();
                  libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec_Spec (*historyClearSpec)(libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec thiz);
                  libdengyui_KBoolean (*isDestructiveSpecValid)(libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec thiz, libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec_Spec spec);
                  libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec_Spec (*searchHistoryClearSpec)(libdengyui_kref_com_dengying_player_domain_ui_ConfirmSpec thiz);
                } ConfirmSpec;
                struct {
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Target (*Target)(const char* name, const char* where, libdengyui_KFloat w, libdengyui_KFloat h, libdengyui_KBoolean hasDisabledState);
                    libdengyui_KFloat (*get_h)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Target thiz);
                    libdengyui_KBoolean (*get_hasDisabledState)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Target thiz);
                    const char* (*get_name)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Target thiz);
                    libdengyui_KFloat (*get_w)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Target thiz);
                    const char* (*get_where)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Target thiz);
                    const char* (*component1)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Target thiz);
                    const char* (*component2)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Target thiz);
                    libdengyui_KFloat (*component3)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Target thiz);
                    libdengyui_KFloat (*component4)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Target thiz);
                    libdengyui_KBoolean (*component5)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Target thiz);
                    libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Target (*copy)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Target thiz, const char* name, const char* where, libdengyui_KFloat w, libdengyui_KFloat h, libdengyui_KBoolean hasDisabledState);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Target thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Target thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Target thiz);
                  } Target;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Verdict (*Verdict)(const char* name, const char* where, libdengyui_KBoolean ok, const char* reason);
                    const char* (*get_name)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Verdict thiz);
                    libdengyui_KBoolean (*get_ok)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Verdict thiz);
                    const char* (*get_reason)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Verdict thiz);
                    const char* (*get_where)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Verdict thiz);
                    const char* (*component1)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Verdict thiz);
                    const char* (*component2)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Verdict thiz);
                    libdengyui_KBoolean (*component3)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Verdict thiz);
                    const char* (*component4)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Verdict thiz);
                    libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Verdict (*copy)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Verdict thiz, const char* name, const char* where, libdengyui_KBoolean ok, const char* reason);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Verdict thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Verdict thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Verdict thiz);
                  } Verdict;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_domain_ui_TouchTarget (*_instance)();
                  libdengyui_KFloat (*get_MIN_TOUCH_DP)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget thiz);
                  libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Verdict (*checkTarget)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget thiz, libdengyui_kref_com_dengying_player_domain_ui_TouchTarget_Target t);
                  libdengyui_kref_kotlin_collections_List (*failingTargets)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget thiz, libdengyui_kref_kotlin_collections_List list);
                  libdengyui_KFloat (*hitSize)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget thiz, libdengyui_KFloat visual, libdengyui_KFloat padding);
                  libdengyui_KFloat (*paddingToReach44)(libdengyui_kref_com_dengying_player_domain_ui_TouchTarget thiz, libdengyui_KFloat visual);
                } TouchTarget;
                struct {
                  struct {
                    struct {
                      libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Verdict (*get)(); /* enum entry for Ok. */
                    } Ok;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Verdict (*get)(); /* enum entry for OkOffList. */
                    } OkOffList;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Verdict (*get)(); /* enum entry for RejectScheme. */
                    } RejectScheme;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Verdict (*get)(); /* enum entry for RejectPrivate. */
                    } RejectPrivate;
                    struct {
                      libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Verdict (*get)(); /* enum entry for RejectEmpty. */
                    } RejectEmpty;
                    libdengyui_KType* (*_type)(void);
                  } Verdict;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Result (*Result)(libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Verdict verdict, libdengyui_KBoolean allowed, libdengyui_KBoolean offList, const char* notice);
                    libdengyui_KBoolean (*get_allowed)(libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Result thiz);
                    const char* (*get_notice)(libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Result thiz);
                    libdengyui_KBoolean (*get_offList)(libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Result thiz);
                    libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Verdict (*get_verdict)(libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Result thiz);
                    libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Verdict (*component1)(libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Result thiz);
                    libdengyui_KBoolean (*component2)(libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Result thiz);
                    libdengyui_KBoolean (*component3)(libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Result thiz);
                    const char* (*component4)(libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Result thiz);
                    libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Result (*copy)(libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Result thiz, libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Verdict verdict, libdengyui_KBoolean allowed, libdengyui_KBoolean offList, const char* notice);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Result thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Result thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Result thiz);
                  } Result;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_domain_ui_UrlGuard (*_instance)();
                  const char* (*get_NOTICE_BAD_SCHEME)(libdengyui_kref_com_dengying_player_domain_ui_UrlGuard thiz);
                  const char* (*get_NOTICE_EMPTY)(libdengyui_kref_com_dengying_player_domain_ui_UrlGuard thiz);
                  const char* (*get_NOTICE_PRIVATE)(libdengyui_kref_com_dengying_player_domain_ui_UrlGuard thiz);
                  libdengyui_kref_com_dengying_player_domain_ui_UrlGuard_Result (*checkPlayUrl)(libdengyui_kref_com_dengying_player_domain_ui_UrlGuard thiz, const char* url, libdengyui_kref_kotlin_collections_List knownHosts);
                  const char* (*hostOf)(libdengyui_kref_com_dengying_player_domain_ui_UrlGuard thiz, const char* url);
                  libdengyui_kref_kotlin_collections_List (*hostsOf)(libdengyui_kref_com_dengying_player_domain_ui_UrlGuard thiz, libdengyui_kref_kotlin_collections_List apis);
                  libdengyui_KBoolean (*isPrivateHost)(libdengyui_kref_com_dengying_player_domain_ui_UrlGuard thiz, const char* h);
                  const char* (*schemeOf)(libdengyui_kref_com_dengying_player_domain_ui_UrlGuard thiz, const char* url);
                } UrlGuard;
              } ui;
            } domain;
            struct {
              struct {
                struct {
                  libdengyui_kref_com_dengying_player_platform_HashAlg (*get)(); /* enum entry for Md5. */
                } Md5;
                struct {
                  libdengyui_kref_com_dengying_player_platform_HashAlg (*get)(); /* enum entry for Sha1. */
                } Sha1;
                struct {
                  libdengyui_kref_com_dengying_player_platform_HashAlg (*get)(); /* enum entry for Sha256. */
                } Sha256;
                libdengyui_KType* (*_type)(void);
              } HashAlg;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_kotlin_collections_Set (*allKeys)(libdengyui_kref_com_dengying_player_platform_KeyValueStore thiz);
                const char* (*getString)(libdengyui_kref_com_dengying_player_platform_KeyValueStore thiz, const char* key, const char* defValue);
                libdengyui_KBoolean (*putString)(libdengyui_kref_com_dengying_player_platform_KeyValueStore thiz, const char* key, const char* value, libdengyui_KBoolean sync);
                libdengyui_KBoolean (*remove)(libdengyui_kref_com_dengying_player_platform_KeyValueStore thiz, const char* key, libdengyui_KBoolean sync);
              } KeyValueStore;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_platform_AesCipher (*_instance)();
                libdengyui_kref_kotlin_ByteArray (*cbcDecrypt)(libdengyui_kref_com_dengying_player_platform_AesCipher thiz, libdengyui_KInt keyBits, libdengyui_kref_kotlin_ByteArray keyBytes, libdengyui_kref_kotlin_ByteArray ivBytes, libdengyui_kref_kotlin_ByteArray cipherBytes);
                libdengyui_kref_kotlin_ByteArray (*cbcEncrypt)(libdengyui_kref_com_dengying_player_platform_AesCipher thiz, libdengyui_KInt keyBits, libdengyui_kref_kotlin_ByteArray keyBytes, libdengyui_kref_kotlin_ByteArray ivBytes, libdengyui_kref_kotlin_ByteArray plain);
                libdengyui_kref_kotlin_ByteArray (*ecbDecrypt)(libdengyui_kref_com_dengying_player_platform_AesCipher thiz, libdengyui_KInt keyBits, libdengyui_kref_kotlin_ByteArray keyBytes, libdengyui_kref_kotlin_ByteArray cipherBytes);
                libdengyui_kref_kotlin_ByteArray (*ecbEncrypt)(libdengyui_kref_com_dengying_player_platform_AesCipher thiz, libdengyui_KInt keyBits, libdengyui_kref_kotlin_ByteArray keyBytes, libdengyui_kref_kotlin_ByteArray plain);
              } AesCipher;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_platform_Clock (*_instance)();
                libdengyui_KLong (*elapsedMs)(libdengyui_kref_com_dengying_player_platform_Clock thiz);
                libdengyui_KLong (*nowMs)(libdengyui_kref_com_dengying_player_platform_Clock thiz);
              } Clock;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_platform_FileKeyValueStore (*FileKeyValueStore)(const char* dir, const char* fileName);
                libdengyui_kref_kotlin_collections_Set (*allKeys)(libdengyui_kref_com_dengying_player_platform_FileKeyValueStore thiz);
                void (*dispose)(libdengyui_kref_com_dengying_player_platform_FileKeyValueStore thiz);
                const char* (*getString)(libdengyui_kref_com_dengying_player_platform_FileKeyValueStore thiz, const char* key, const char* defValue);
                libdengyui_KBoolean (*putString)(libdengyui_kref_com_dengying_player_platform_FileKeyValueStore thiz, const char* key, const char* value, libdengyui_KBoolean sync);
                libdengyui_KBoolean (*remove)(libdengyui_kref_com_dengying_player_platform_FileKeyValueStore thiz, const char* key, libdengyui_KBoolean sync);
              } FileKeyValueStore;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_platform_Hash (*_instance)();
                libdengyui_kref_kotlin_ByteArray (*digest)(libdengyui_kref_com_dengying_player_platform_Hash thiz, libdengyui_kref_com_dengying_player_platform_HashAlg alg, libdengyui_kref_kotlin_ByteArray bytes);
              } Hash;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_platform_Log (*_instance)();
                void (*w)(libdengyui_kref_com_dengying_player_platform_Log thiz, const char* tag, const char* message);
                void (*w_)(libdengyui_kref_com_dengying_player_platform_Log thiz, const char* tag, const char* message, libdengyui_kref_kotlin_Throwable error);
              } Log;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_platform_PlatformLock (*PlatformLock)();
                void (*dispose)(libdengyui_kref_com_dengying_player_platform_PlatformLock thiz);
              } PlatformLock;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_platform_RsaCipher (*_instance)();
                libdengyui_kref_kotlin_ByteArray (*decrypt)(libdengyui_kref_com_dengying_player_platform_RsaCipher thiz, libdengyui_kref_kotlin_ByteArray privateKeyDer, libdengyui_kref_kotlin_ByteArray cipherBytes);
                libdengyui_kref_kotlin_ByteArray (*encrypt)(libdengyui_kref_com_dengying_player_platform_RsaCipher thiz, libdengyui_kref_kotlin_ByteArray publicKeyDer, libdengyui_kref_kotlin_ByteArray plain);
                libdengyui_KInt (*maxPlainChunk)(libdengyui_kref_com_dengying_player_platform_RsaCipher thiz, libdengyui_kref_kotlin_ByteArray publicKeyDer);
              } RsaCipher;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_platform_TimeFormat (*_instance)();
                const char* (*compactLocalTimestamp)(libdengyui_kref_com_dengying_player_platform_TimeFormat thiz, libdengyui_KLong epochMs);
              } TimeFormat;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_platform_UrlCodec (*_instance)();
                const char* (*encode)(libdengyui_kref_com_dengying_player_platform_UrlCodec thiz, const char* s);
                const char* (*encodeForm)(libdengyui_kref_com_dengying_player_platform_UrlCodec thiz, const char* s);
              } UrlCodec;
              const char* (*percentDecode)(const char* s);
              const char* (*percentEncode)(const char* s, libdengyui_KBoolean spaceAsPlus);
              libdengyui_kref_kotlinx_coroutines_CoroutineDispatcher (*get_ioDispatcher)();
            } platform;
            struct {
              struct {
                struct {
                  struct {
                    libdengyui_kref_com_dengying_player_playback_PlaybackSession_Status (*get)(); /* enum entry for Idle. */
                  } Idle;
                  struct {
                    libdengyui_kref_com_dengying_player_playback_PlaybackSession_Status (*get)(); /* enum entry for Resolving. */
                  } Resolving;
                  struct {
                    libdengyui_kref_com_dengying_player_playback_PlaybackSession_Status (*get)(); /* enum entry for Buffering. */
                  } Buffering;
                  struct {
                    libdengyui_kref_com_dengying_player_playback_PlaybackSession_Status (*get)(); /* enum entry for Playing. */
                  } Playing;
                  struct {
                    libdengyui_kref_com_dengying_player_playback_PlaybackSession_Status (*get)(); /* enum entry for Paused. */
                  } Paused;
                  struct {
                    libdengyui_kref_com_dengying_player_playback_PlaybackSession_Status (*get)(); /* enum entry for Error. */
                  } Error;
                  libdengyui_KType* (*_type)(void);
                } Status;
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_playback_PlaybackSession_State (*State)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_Status status, const char* url, const char* headers, const char* title, const char* vodName, libdengyui_KLong vodId, const char* source, const char* vodUri, libdengyui_KInt episodeIndex, const char* episodeName, libdengyui_KLong curTime, libdengyui_KLong totalTime, libdengyui_KBoolean buffering, const char* errMsg);
                  libdengyui_KBoolean (*get_buffering)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  libdengyui_KLong (*get_curTime)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  libdengyui_KInt (*get_episodeIndex)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  const char* (*get_episodeName)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  const char* (*get_errMsg)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  libdengyui_KBoolean (*get_hasSession)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  const char* (*get_headers)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  const char* (*get_source)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  libdengyui_kref_com_dengying_player_playback_PlaybackSession_Status (*get_status)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  const char* (*get_title)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  libdengyui_KLong (*get_totalTime)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  const char* (*get_url)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  libdengyui_KLong (*get_vodId)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  const char* (*get_vodName)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  const char* (*get_vodUri)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  libdengyui_kref_com_dengying_player_playback_PlaybackSession_Status (*component1)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  const char* (*component10)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  libdengyui_KLong (*component11)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  libdengyui_KLong (*component12)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  libdengyui_KBoolean (*component13)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  const char* (*component14)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  const char* (*component2)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  const char* (*component3)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  const char* (*component4)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  const char* (*component5)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  libdengyui_KLong (*component6)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  const char* (*component7)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  const char* (*component8)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  libdengyui_KInt (*component9)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  libdengyui_kref_com_dengying_player_playback_PlaybackSession_State (*copy)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz, libdengyui_kref_com_dengying_player_playback_PlaybackSession_Status status, const char* url, const char* headers, const char* title, const char* vodName, libdengyui_KLong vodId, const char* source, const char* vodUri, libdengyui_KInt episodeIndex, const char* episodeName, libdengyui_KLong curTime, libdengyui_KLong totalTime, libdengyui_KBoolean buffering, const char* errMsg);
                  libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz, libdengyui_kref_kotlin_Any other);
                  libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                  const char* (*toString)(libdengyui_kref_com_dengying_player_playback_PlaybackSession_State thiz);
                } State;
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_playback_PlaybackSession (*_instance)();
                libdengyui_kref_kotlinx_coroutines_flow_StateFlow (*get_state)(libdengyui_kref_com_dengying_player_playback_PlaybackSession thiz);
                libdengyui_KLong (*currentPosition)(libdengyui_kref_com_dengying_player_playback_PlaybackSession thiz);
                libdengyui_kref_com_dengying_player_playback_PlaybackSession_State (*currentState)(libdengyui_kref_com_dengying_player_playback_PlaybackSession thiz);
                libdengyui_KLong (*duration)(libdengyui_kref_com_dengying_player_playback_PlaybackSession thiz);
                void (*init)(libdengyui_kref_com_dengying_player_playback_PlaybackSession thiz, libdengyui_kref_com_dengying_player_player_PlayerEngineHost host);
                libdengyui_KBoolean (*isPlaying)(libdengyui_kref_com_dengying_player_playback_PlaybackSession thiz);
                void (*pause)(libdengyui_kref_com_dengying_player_playback_PlaybackSession thiz);
                void (*play)(libdengyui_kref_com_dengying_player_playback_PlaybackSession thiz, libdengyui_kref_com_dengying_player_data_source_SourceRepository repo, const char* rawUrl, const char* title, const char* vodName, libdengyui_KLong vodId, const char* source, const char* vodUri, libdengyui_KInt episodeIndex, const char* episodeName, const char* preResolved, const char* preHeaders, libdengyui_KLong startAtMs, libdengyui_kref_kotlin_collections_List altUrls);
                void (*registerPlayback)(libdengyui_kref_com_dengying_player_playback_PlaybackSession thiz, const char* url, const char* headers, const char* title, const char* vodName, libdengyui_KLong vodId, const char* source, const char* vodUri, libdengyui_KInt episodeIndex, const char* episodeName);
                void (*resume)(libdengyui_kref_com_dengying_player_playback_PlaybackSession thiz);
                void (*seekTo)(libdengyui_kref_com_dengying_player_playback_PlaybackSession thiz, libdengyui_KLong positionMs);
                void (*setSpeed)(libdengyui_kref_com_dengying_player_playback_PlaybackSession thiz, libdengyui_KFloat speed);
                void (*stop)(libdengyui_kref_com_dengying_player_playback_PlaybackSession thiz);
                void (*togglePlay)(libdengyui_kref_com_dengying_player_playback_PlaybackSession thiz);
              } PlaybackSession;
            } playback;
            struct {
              struct {
                struct {
                  libdengyui_kref_com_dengying_player_player_PlaybackErrorKind (*get)(); /* enum entry for Network. */
                } Network;
                struct {
                  libdengyui_kref_com_dengying_player_player_PlaybackErrorKind (*get)(); /* enum entry for SourceGone. */
                } SourceGone;
                struct {
                  libdengyui_kref_com_dengying_player_player_PlaybackErrorKind (*get)(); /* enum entry for Decode. */
                } Decode;
                struct {
                  libdengyui_kref_com_dengying_player_player_PlaybackErrorKind (*get)(); /* enum entry for Unknown. */
                } Unknown;
                libdengyui_KType* (*_type)(void);
              } PlaybackErrorKind;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_KLong (*get_currentPosition)(libdengyui_kref_com_dengying_player_player_PlayerEngine thiz);
                libdengyui_KLong (*get_duration)(libdengyui_kref_com_dengying_player_player_PlayerEngine thiz);
                libdengyui_KBoolean (*get_isPlaying)(libdengyui_kref_com_dengying_player_player_PlayerEngine thiz);
                void (*addListener)(libdengyui_kref_com_dengying_player_player_PlayerEngine thiz, libdengyui_kref_com_dengying_player_player_PlayerListener listener);
                void (*pause)(libdengyui_kref_com_dengying_player_player_PlayerEngine thiz);
                void (*play)(libdengyui_kref_com_dengying_player_player_PlayerEngine thiz);
                void (*release)(libdengyui_kref_com_dengying_player_player_PlayerEngine thiz);
                void (*seekTo)(libdengyui_kref_com_dengying_player_player_PlayerEngine thiz, libdengyui_KLong positionMs);
                void (*setMedia)(libdengyui_kref_com_dengying_player_player_PlayerEngine thiz, const char* url, const char* headersJson, libdengyui_KLong startPositionMs);
                void (*setSpeed)(libdengyui_kref_com_dengying_player_player_PlayerEngine thiz, libdengyui_KFloat speed);
                libdengyui_kref_kotlin_Any (*viewHandle)(libdengyui_kref_com_dengying_player_player_PlayerEngine thiz);
              } PlayerEngine;
              struct {
                libdengyui_KType* (*_type)(void);
                void (*onBuffering)(libdengyui_kref_com_dengying_player_player_PlayerListener thiz, libdengyui_KBoolean active);
                void (*onCompleted)(libdengyui_kref_com_dengying_player_player_PlayerListener thiz);
                void (*onError)(libdengyui_kref_com_dengying_player_player_PlayerListener thiz, libdengyui_kref_com_dengying_player_player_PlaybackErrorKind kind, libdengyui_KInt code, const char* message);
                void (*onPaused)(libdengyui_kref_com_dengying_player_player_PlayerListener thiz);
                void (*onPlaying)(libdengyui_kref_com_dengying_player_player_PlayerListener thiz);
                void (*onReady)(libdengyui_kref_com_dengying_player_player_PlayerListener thiz, libdengyui_KLong durationMs);
                void (*onVideoSize)(libdengyui_kref_com_dengying_player_player_PlayerListener thiz, libdengyui_KInt width, libdengyui_KInt height);
              } PlayerListener;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_player_PlayerEngine (*engine)(libdengyui_kref_com_dengying_player_player_PlayerEngineHost thiz);
                void (*forceRelease)(libdengyui_kref_com_dengying_player_player_PlayerEngineHost thiz);
                libdengyui_kref_com_dengying_player_player_PlayerEngine (*peek)(libdengyui_kref_com_dengying_player_player_PlayerEngineHost thiz);
                void (*surfaceActive)(libdengyui_kref_com_dengying_player_player_PlayerEngineHost thiz);
                libdengyui_KInt (*surfaceCount)(libdengyui_kref_com_dengying_player_player_PlayerEngineHost thiz);
                void (*surfaceInactive)(libdengyui_kref_com_dengying_player_player_PlayerEngineHost thiz);
              } PlayerEngineHost;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_player_PlaybackProbe (*_instance)();
                void (*dumpSharePage)(libdengyui_kref_com_dengying_player_player_PlaybackProbe thiz, const char* url);
                void (*init)(libdengyui_kref_com_dengying_player_player_PlaybackProbe thiz, const char* dir);
                void (*note)(libdengyui_kref_com_dengying_player_player_PlaybackProbe thiz, const char* tag, const char* detail);
                void (*reset)(libdengyui_kref_com_dengying_player_player_PlaybackProbe thiz);
              } PlaybackProbe;
            } player;
            struct {
              struct {
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_ui_UiDeps_Companion (*_instance)();
                  libdengyui_KInt (*get_ALT_CACHE_MAX)(libdengyui_kref_com_dengying_player_ui_UiDeps_Companion thiz);
                  libdengyui_KInt (*get_DETAIL_CACHE_MAX)(libdengyui_kref_com_dengying_player_ui_UiDeps_Companion thiz);
                  const char* (*detailCacheKey)(libdengyui_kref_com_dengying_player_ui_UiDeps_Companion thiz, const char* source, libdengyui_KLong vodId);
                } Companion;
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_ui_viewmodel_LruCache (*get_altSourceCache)(libdengyui_kref_com_dengying_player_ui_UiDeps thiz);
                libdengyui_kref_com_dengying_player_ui_viewmodel_LruCache (*get_detailCache)(libdengyui_kref_com_dengying_player_ui_UiDeps thiz);
                libdengyui_kref_com_dengying_player_player_PlayerEngineHost (*get_engineHost)(libdengyui_kref_com_dengying_player_ui_UiDeps thiz);
                libdengyui_KBoolean (*get_isDebug)(libdengyui_kref_com_dengying_player_ui_UiDeps thiz);
                libdengyui_kref_com_dengying_player_data_cache_PageCache (*get_pageCache)(libdengyui_kref_com_dengying_player_ui_UiDeps thiz);
                libdengyui_kref_com_dengying_player_data_source_SourceRepository (*get_sources)(libdengyui_kref_com_dengying_player_ui_UiDeps thiz);
                libdengyui_kref_com_dengying_player_data_store_Store (*get_store)(libdengyui_kref_com_dengying_player_ui_UiDeps thiz);
              } UiDeps;
              struct {
                libdengyui_KType* (*_type)(void);
                libdengyui_kref_com_dengying_player_ui_UiDepsHolder (*_instance)();
                libdengyui_kref_com_dengying_player_ui_UiDeps (*get)(libdengyui_kref_com_dengying_player_ui_UiDepsHolder thiz);
                void (*init)(libdengyui_kref_com_dengying_player_ui_UiDepsHolder thiz, libdengyui_kref_com_dengying_player_ui_UiDeps deps);
                void (*resetForTest)(libdengyui_kref_com_dengying_player_ui_UiDepsHolder thiz);
              } UiDepsHolder;
              struct {
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_ui_components_PosterDecoder (*_instance)();
                  libdengyui_kref_androidx_compose_ui_graphics_ImageBitmap (*decode)(libdengyui_kref_com_dengying_player_ui_components_PosterDecoder thiz, libdengyui_kref_kotlin_ByteArray bytes);
                } PosterDecoder;
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_ui_components_PosterFetch (*_instance)();
                } PosterFetch;
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_ui_components_DyIcons (*_instance)();
                  libdengyui_kref_com_dengying_player_ui_components_DyIcon (*get_ArrowBack)(libdengyui_kref_com_dengying_player_ui_components_DyIcons thiz);
                  libdengyui_kref_com_dengying_player_ui_components_DyIcon (*get_Brightness)(libdengyui_kref_com_dengying_player_ui_components_DyIcons thiz);
                  libdengyui_kref_com_dengying_player_ui_components_DyIcon (*get_ClearAll)(libdengyui_kref_com_dengying_player_ui_components_DyIcons thiz);
                  libdengyui_kref_com_dengying_player_ui_components_DyIcon (*get_Close)(libdengyui_kref_com_dengying_player_ui_components_DyIcons thiz);
                  libdengyui_kref_com_dengying_player_ui_components_DyIcon (*get_ErrorFace)(libdengyui_kref_com_dengying_player_ui_components_DyIcons thiz);
                  libdengyui_kref_com_dengying_player_ui_components_DyIcon (*get_FastForward)(libdengyui_kref_com_dengying_player_ui_components_DyIcons thiz);
                  libdengyui_kref_com_dengying_player_ui_components_DyIcon (*get_FastRewind)(libdengyui_kref_com_dengying_player_ui_components_DyIcons thiz);
                  libdengyui_kref_com_dengying_player_ui_components_DyIcon (*get_Fullscreen)(libdengyui_kref_com_dengying_player_ui_components_DyIcons thiz);
                  libdengyui_kref_com_dengying_player_ui_components_DyIcon (*get_FullscreenExit)(libdengyui_kref_com_dengying_player_ui_components_DyIcons thiz);
                  libdengyui_kref_com_dengying_player_ui_components_DyIcon (*get_History)(libdengyui_kref_com_dengying_player_ui_components_DyIcons thiz);
                  libdengyui_kref_com_dengying_player_ui_components_DyIcon (*get_Lantern)(libdengyui_kref_com_dengying_player_ui_components_DyIcons thiz);
                  libdengyui_kref_com_dengying_player_ui_components_DyIcon (*get_Pause)(libdengyui_kref_com_dengying_player_ui_components_DyIcons thiz);
                  libdengyui_kref_com_dengying_player_ui_components_DyIcon (*get_Person)(libdengyui_kref_com_dengying_player_ui_components_DyIcons thiz);
                  libdengyui_kref_com_dengying_player_ui_components_DyIcon (*get_Play)(libdengyui_kref_com_dengying_player_ui_components_DyIcons thiz);
                  libdengyui_kref_com_dengying_player_ui_components_DyIcon (*get_Refresh)(libdengyui_kref_com_dengying_player_ui_components_DyIcons thiz);
                  libdengyui_kref_com_dengying_player_ui_components_DyIcon (*get_Search)(libdengyui_kref_com_dengying_player_ui_components_DyIcons thiz);
                  libdengyui_kref_com_dengying_player_ui_components_DyIcon (*get_SkipNext)(libdengyui_kref_com_dengying_player_ui_components_DyIcons thiz);
                  libdengyui_kref_com_dengying_player_ui_components_DyIcon (*get_SkipPrevious)(libdengyui_kref_com_dengying_player_ui_components_DyIcons thiz);
                  libdengyui_kref_com_dengying_player_ui_components_DyIcon (*get_Star)(libdengyui_kref_com_dengying_player_ui_components_DyIcons thiz);
                  libdengyui_kref_com_dengying_player_ui_components_DyIcon (*get_StarOutline)(libdengyui_kref_com_dengying_player_ui_components_DyIcons thiz);
                  libdengyui_kref_com_dengying_player_ui_components_DyIcon (*get_Volume)(libdengyui_kref_com_dengying_player_ui_components_DyIcons thiz);
                  libdengyui_kref_kotlin_collections_List (*get_all)(libdengyui_kref_com_dengying_player_ui_components_DyIcons thiz);
                } DyIcons;
                struct {
                  libdengyui_KType* (*_type)(void);
                } DyIcon;
              } components;
              struct {
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_ui_navigation_Routes (*_instance)();
                  const char* (*get_DETAIL)(libdengyui_kref_com_dengying_player_ui_navigation_Routes thiz);
                  const char* (*get_FAVORITES)(libdengyui_kref_com_dengying_player_ui_navigation_Routes thiz);
                  const char* (*get_HISTORY)(libdengyui_kref_com_dengying_player_ui_navigation_Routes thiz);
                  const char* (*get_INDEX)(libdengyui_kref_com_dengying_player_ui_navigation_Routes thiz);
                  const char* (*get_PLAYER)(libdengyui_kref_com_dengying_player_ui_navigation_Routes thiz);
                  const char* (*get_SEARCH)(libdengyui_kref_com_dengying_player_ui_navigation_Routes thiz);
                  const char* (*get_SEP)(libdengyui_kref_com_dengying_player_ui_navigation_Routes thiz);
                  const char* (*get_SOURCE_STATUS)(libdengyui_kref_com_dengying_player_ui_navigation_Routes thiz);
                  const char* (*detail)(libdengyui_kref_com_dengying_player_ui_navigation_Routes thiz, libdengyui_KLong vodId, const char* source, const char* name, const char* pic, const char* vodUri, libdengyui_KInt resumeEpisode, libdengyui_KBoolean autoPlay);
                  const char* (*player)(libdengyui_kref_com_dengying_player_ui_navigation_Routes thiz, const char* url, const char* title, libdengyui_KLong vodId, const char* source, const char* vodName, const char* vodPic, const char* vodUri, const char* lineFrom, libdengyui_KInt episodeIndex, const char* episodeName, libdengyui_kref_kotlin_collections_List altUrls, libdengyui_kref_kotlin_collections_List epUrls, libdengyui_kref_kotlin_collections_List epNames, libdengyui_KLong startAtMs, const char* headersJson);
                  const char* (*search)(libdengyui_kref_com_dengying_player_ui_navigation_Routes thiz, const char* keyword);
                } Routes;
                const char* (*dec)(const char* s);
                const char* (*enc)(const char* s);
                libdengyui_kref_kotlin_collections_Map (*queryOf)(const char* s);
                libdengyui_kref_kotlin_collections_List (*splitList)(const char* s);
              } navigation;
              struct {
                void* (*mainArkUIViewController)(void* env);
              } ohos;
              struct {
                struct {
                  libdengyui_KType* (*_type)(void);
                  void (*enter)(libdengyui_kref_com_dengying_player_ui_platform_FullscreenState thiz, libdengyui_KBoolean landscape);
                  void (*exit)(libdengyui_kref_com_dengying_player_ui_platform_FullscreenState thiz);
                } FullscreenState;
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_KFloat (*adjust)(libdengyui_kref_com_dengying_player_ui_platform_BrightnessController thiz, libdengyui_KFloat base, libdengyui_KFloat fraction);
                  libdengyui_KFloat (*current)(libdengyui_kref_com_dengying_player_ui_platform_BrightnessController thiz);
                  void (*restore)(libdengyui_kref_com_dengying_player_ui_platform_BrightnessController thiz);
                } BrightnessController;
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_KFloat (*adjust)(libdengyui_kref_com_dengying_player_ui_platform_VolumeController thiz, libdengyui_KFloat base, libdengyui_KFloat fraction);
                  libdengyui_KFloat (*currentFraction)(libdengyui_kref_com_dengying_player_ui_platform_VolumeController thiz);
                } VolumeController;
              } platform;
              struct {
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_ui_theme_Palette (*_instance)();
                  libdengyui_KULong (*get_AppBg)(libdengyui_kref_com_dengying_player_ui_theme_Palette thiz);
                  libdengyui_KULong (*get_BadgeScrim)(libdengyui_kref_com_dengying_player_ui_theme_Palette thiz);
                  libdengyui_KULong (*get_Brand)(libdengyui_kref_com_dengying_player_ui_theme_Palette thiz);
                  libdengyui_KULong (*get_BrandSecondary)(libdengyui_kref_com_dengying_player_ui_theme_Palette thiz);
                  libdengyui_KULong (*get_CardBg)(libdengyui_kref_com_dengying_player_ui_theme_Palette thiz);
                  libdengyui_KULong (*get_Danger)(libdengyui_kref_com_dengying_player_ui_theme_Palette thiz);
                  libdengyui_KULong (*get_Divider)(libdengyui_kref_com_dengying_player_ui_theme_Palette thiz);
                  libdengyui_KULong (*get_SearchBg)(libdengyui_kref_com_dengying_player_ui_theme_Palette thiz);
                  libdengyui_KULong (*get_TextPrimary)(libdengyui_kref_com_dengying_player_ui_theme_Palette thiz);
                  libdengyui_KULong (*get_TextSecondary)(libdengyui_kref_com_dengying_player_ui_theme_Palette thiz);
                } Palette;
              } theme;
              struct {
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_ui_viewmodel_AltItem (*AltItem)(const char* sourceKey, const char* sourceName, libdengyui_KLong vodId, const char* vodName);
                  const char* (*get_sourceKey)(libdengyui_kref_com_dengying_player_ui_viewmodel_AltItem thiz);
                  const char* (*get_sourceName)(libdengyui_kref_com_dengying_player_ui_viewmodel_AltItem thiz);
                  libdengyui_KLong (*get_vodId)(libdengyui_kref_com_dengying_player_ui_viewmodel_AltItem thiz);
                  const char* (*get_vodName)(libdengyui_kref_com_dengying_player_ui_viewmodel_AltItem thiz);
                  const char* (*component1)(libdengyui_kref_com_dengying_player_ui_viewmodel_AltItem thiz);
                  const char* (*component2)(libdengyui_kref_com_dengying_player_ui_viewmodel_AltItem thiz);
                  libdengyui_KLong (*component3)(libdengyui_kref_com_dengying_player_ui_viewmodel_AltItem thiz);
                  const char* (*component4)(libdengyui_kref_com_dengying_player_ui_viewmodel_AltItem thiz);
                  libdengyui_kref_com_dengying_player_ui_viewmodel_AltItem (*copy)(libdengyui_kref_com_dengying_player_ui_viewmodel_AltItem thiz, const char* sourceKey, const char* sourceName, libdengyui_KLong vodId, const char* vodName);
                  libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_ui_viewmodel_AltItem thiz, libdengyui_kref_kotlin_Any other);
                  libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_ui_viewmodel_AltItem thiz);
                  const char* (*toString)(libdengyui_kref_com_dengying_player_ui_viewmodel_AltItem thiz);
                } AltItem;
                struct {
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState (*UiState)(libdengyui_kref_com_dengying_player_core_VideoItem item, libdengyui_kref_kotlin_collections_List lines, libdengyui_KInt lineIdx, libdengyui_KBoolean loading, libdengyui_KBoolean faved, const char* errMsg, libdengyui_kref_kotlin_collections_List altSources, libdengyui_KBoolean altLoading, libdengyui_KBoolean descOrder, const char* playGap, const char* resumeNotice, const char* epNotice, libdengyui_KBoolean epShowGrid, libdengyui_kref_com_dengying_player_core_HistoryItem lastHistory);
                    libdengyui_KBoolean (*get_altLoading)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    libdengyui_kref_kotlin_collections_List (*get_altSources)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    libdengyui_KBoolean (*get_descOrder)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    const char* (*get_epNotice)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    libdengyui_KBoolean (*get_epShowGrid)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    const char* (*get_errMsg)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    libdengyui_KBoolean (*get_faved)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    libdengyui_kref_com_dengying_player_core_VideoItem (*get_item)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    libdengyui_kref_com_dengying_player_core_HistoryItem (*get_lastHistory)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    libdengyui_KInt (*get_lineIdx)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    libdengyui_kref_kotlin_collections_List (*get_lines)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    libdengyui_KBoolean (*get_loading)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    const char* (*get_playGap)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    const char* (*get_resumeNotice)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    libdengyui_kref_com_dengying_player_core_VideoItem (*component1)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    const char* (*component10)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    const char* (*component11)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    const char* (*component12)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    libdengyui_KBoolean (*component13)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    libdengyui_kref_com_dengying_player_core_HistoryItem (*component14)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    libdengyui_kref_kotlin_collections_List (*component2)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    libdengyui_KInt (*component3)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    libdengyui_KBoolean (*component4)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    libdengyui_KBoolean (*component5)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    const char* (*component6)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    libdengyui_kref_kotlin_collections_List (*component7)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    libdengyui_KBoolean (*component8)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    libdengyui_KBoolean (*component9)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState (*copy)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz, libdengyui_kref_com_dengying_player_core_VideoItem item, libdengyui_kref_kotlin_collections_List lines, libdengyui_KInt lineIdx, libdengyui_KBoolean loading, libdengyui_KBoolean faved, const char* errMsg, libdengyui_kref_kotlin_collections_List altSources, libdengyui_KBoolean altLoading, libdengyui_KBoolean descOrder, const char* playGap, const char* resumeNotice, const char* epNotice, libdengyui_KBoolean epShowGrid, libdengyui_kref_com_dengying_player_core_HistoryItem lastHistory);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState thiz);
                  } UiState;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState (*InlineState)(libdengyui_KBoolean active, const char* title, libdengyui_KBoolean isPlaying, libdengyui_KLong curTime, libdengyui_KLong totalTime, libdengyui_KBoolean buffering, const char* errMsg, const char* status, libdengyui_KBoolean hidden);
                    libdengyui_KBoolean (*get_active)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState thiz);
                    libdengyui_KBoolean (*get_buffering)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState thiz);
                    libdengyui_KLong (*get_curTime)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState thiz);
                    const char* (*get_errMsg)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState thiz);
                    libdengyui_KBoolean (*get_hidden)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState thiz);
                    libdengyui_KBoolean (*get_isPlaying)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState thiz);
                    const char* (*get_status)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState thiz);
                    const char* (*get_title)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState thiz);
                    libdengyui_KLong (*get_totalTime)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState thiz);
                    libdengyui_KBoolean (*component1)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState thiz);
                    const char* (*component2)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState thiz);
                    libdengyui_KBoolean (*component3)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState thiz);
                    libdengyui_KLong (*component4)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState thiz);
                    libdengyui_KLong (*component5)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState thiz);
                    libdengyui_KBoolean (*component6)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState thiz);
                    const char* (*component7)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState thiz);
                    const char* (*component8)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState thiz);
                    libdengyui_KBoolean (*component9)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState thiz);
                    libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState (*copy)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState thiz, libdengyui_KBoolean active, const char* title, libdengyui_KBoolean isPlaying, libdengyui_KLong curTime, libdengyui_KLong totalTime, libdengyui_KBoolean buffering, const char* errMsg, const char* status, libdengyui_KBoolean hidden);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState thiz);
                  } InlineState;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel (*DetailViewModel)(libdengyui_KLong vodId, const char* source, const char* name, const char* pic, const char* vodUri, libdengyui_KInt resumeEpisode, libdengyui_KBoolean autoPlayRequested, libdengyui_kref_com_dengying_player_data_source_SourceRepository repo, libdengyui_kref_com_dengying_player_data_store_Store store, libdengyui_kref_com_dengying_player_player_PlayerEngineHost engineHost, libdengyui_kref_com_dengying_player_ui_UiDeps deps);
                  libdengyui_kref_com_dengying_player_player_PlayerEngine (*get_inlineEngine)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz);
                  libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_InlineState (*get_inlineState)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz);
                  const char* (*get_pendingPlayerRoute)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz);
                  libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel_UiState (*get_state)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz);
                  void (*attachInlineSurface)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz);
                  void (*clearInlineUi)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz);
                  void (*consumeInlineError)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz);
                  void (*consumeNotice)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz);
                  void (*consumePlayerRoute)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz);
                  libdengyui_kref_kotlin_collections_List (*currentEps)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz);
                  libdengyui_kref_kotlin_collections_List (*displayEps)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz);
                  libdengyui_KInt (*epRealIndex)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz, libdengyui_KInt displayIdx);
                  libdengyui_KBoolean (*isResumeEp)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz, libdengyui_KInt idx);
                  void (*loadDetail)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz);
                  void (*onToggleFav)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz);
                  void (*pauseInline)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz);
                  void (*playEpisode)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz, libdengyui_KInt realIdx, libdengyui_KLong startAtMs, const char* overrideUrl, const char* overrideHeaders);
                  void (*playInline)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz, libdengyui_KInt realIdx);
                  void (*releaseInline)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz);
                  void (*resumeInlineIfPlaying)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz);
                  void (*seekInline)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz, libdengyui_KLong positionMs);
                  void (*selectLine)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz, libdengyui_KInt idx);
                  void (*switchToSource)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz, libdengyui_kref_com_dengying_player_ui_viewmodel_AltItem target);
                  void (*toFullscreen)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz);
                  void (*toggleInlinePlay)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz);
                  void (*toggleOrder)(libdengyui_kref_com_dengying_player_ui_viewmodel_DetailViewModel thiz);
                } DetailViewModel;
                struct {
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_ui_viewmodel_FavoritesViewModel_UiState (*UiState)(libdengyui_kref_kotlin_collections_List list, const char* newerNotice, const char* tip);
                    libdengyui_kref_kotlin_collections_List (*get_list)(libdengyui_kref_com_dengying_player_ui_viewmodel_FavoritesViewModel_UiState thiz);
                    const char* (*get_newerNotice)(libdengyui_kref_com_dengying_player_ui_viewmodel_FavoritesViewModel_UiState thiz);
                    const char* (*get_tip)(libdengyui_kref_com_dengying_player_ui_viewmodel_FavoritesViewModel_UiState thiz);
                    libdengyui_kref_kotlin_collections_List (*component1)(libdengyui_kref_com_dengying_player_ui_viewmodel_FavoritesViewModel_UiState thiz);
                    const char* (*component2)(libdengyui_kref_com_dengying_player_ui_viewmodel_FavoritesViewModel_UiState thiz);
                    const char* (*component3)(libdengyui_kref_com_dengying_player_ui_viewmodel_FavoritesViewModel_UiState thiz);
                    libdengyui_kref_com_dengying_player_ui_viewmodel_FavoritesViewModel_UiState (*copy)(libdengyui_kref_com_dengying_player_ui_viewmodel_FavoritesViewModel_UiState thiz, libdengyui_kref_kotlin_collections_List list, const char* newerNotice, const char* tip);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_ui_viewmodel_FavoritesViewModel_UiState thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_ui_viewmodel_FavoritesViewModel_UiState thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_ui_viewmodel_FavoritesViewModel_UiState thiz);
                  } UiState;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_ui_viewmodel_FavoritesViewModel (*FavoritesViewModel)(libdengyui_kref_com_dengying_player_data_store_Store store);
                  libdengyui_kref_com_dengying_player_ui_viewmodel_FavoritesViewModel_UiState (*get_state)(libdengyui_kref_com_dengying_player_ui_viewmodel_FavoritesViewModel thiz);
                  void (*consumeTip)(libdengyui_kref_com_dengying_player_ui_viewmodel_FavoritesViewModel thiz);
                  void (*reload)(libdengyui_kref_com_dengying_player_ui_viewmodel_FavoritesViewModel thiz);
                  void (*remove)(libdengyui_kref_com_dengying_player_ui_viewmodel_FavoritesViewModel thiz, libdengyui_kref_com_dengying_player_core_FavoriteItem item);
                } FavoritesViewModel;
                struct {
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel_UiState (*UiState)(libdengyui_kref_kotlin_collections_List list, const char* newerNotice, const char* tip, libdengyui_KBoolean showClearConfirm);
                    libdengyui_kref_kotlin_collections_List (*get_list)(libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel_UiState thiz);
                    const char* (*get_newerNotice)(libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel_UiState thiz);
                    libdengyui_KBoolean (*get_showClearConfirm)(libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel_UiState thiz);
                    const char* (*get_tip)(libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel_UiState thiz);
                    libdengyui_kref_kotlin_collections_List (*component1)(libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel_UiState thiz);
                    const char* (*component2)(libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel_UiState thiz);
                    const char* (*component3)(libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel_UiState thiz);
                    libdengyui_KBoolean (*component4)(libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel_UiState thiz);
                    libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel_UiState (*copy)(libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel_UiState thiz, libdengyui_kref_kotlin_collections_List list, const char* newerNotice, const char* tip, libdengyui_KBoolean showClearConfirm);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel_UiState thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel_UiState thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel_UiState thiz);
                  } UiState;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel (*HistoryViewModel)(libdengyui_kref_com_dengying_player_data_store_Store store);
                  libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel_UiState (*get_state)(libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel thiz);
                  void (*consumeTip)(libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel thiz);
                  void (*dismissClear)(libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel thiz);
                  void (*doClear)(libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel thiz);
                  void (*reload)(libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel thiz);
                  void (*requestClear)(libdengyui_kref_com_dengying_player_ui_viewmodel_HistoryViewModel thiz);
                } HistoryViewModel;
                struct {
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState (*CatState)(libdengyui_kref_kotlin_collections_List items, libdengyui_KBoolean loading, const char* errMsg, const char* sourceName, libdengyui_KInt page, libdengyui_KBoolean hasMore, libdengyui_KBoolean loadingMore, libdengyui_KBoolean refining, libdengyui_kref_kotlin_collections_List ranking, libdengyui_KBoolean started);
                    const char* (*get_errMsg)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz);
                    libdengyui_KBoolean (*get_hasMore)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz);
                    libdengyui_kref_kotlin_collections_List (*get_items)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz);
                    libdengyui_KBoolean (*get_loading)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz);
                    libdengyui_KBoolean (*get_loadingMore)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz);
                    libdengyui_KInt (*get_page)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz);
                    libdengyui_kref_kotlin_collections_List (*get_ranking)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz);
                    libdengyui_KBoolean (*get_refining)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz);
                    const char* (*get_sourceName)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz);
                    libdengyui_KBoolean (*get_started)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz);
                    libdengyui_kref_kotlin_collections_List (*component1)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz);
                    libdengyui_KBoolean (*component10)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz);
                    libdengyui_KBoolean (*component2)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz);
                    const char* (*component3)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz);
                    const char* (*component4)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz);
                    libdengyui_KInt (*component5)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz);
                    libdengyui_KBoolean (*component6)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz);
                    libdengyui_KBoolean (*component7)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz);
                    libdengyui_KBoolean (*component8)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz);
                    libdengyui_kref_kotlin_collections_List (*component9)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz);
                    libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState (*copy)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz, libdengyui_kref_kotlin_collections_List items, libdengyui_KBoolean loading, const char* errMsg, const char* sourceName, libdengyui_KInt page, libdengyui_KBoolean hasMore, libdengyui_KBoolean loadingMore, libdengyui_KBoolean refining, libdengyui_kref_kotlin_collections_List ranking, libdengyui_KBoolean started);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState thiz);
                  } CatState;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_UiState (*UiState)(libdengyui_kref_kotlin_collections_List categories, libdengyui_KInt currentTab, libdengyui_kref_kotlin_collections_Map cats);
                    libdengyui_kref_kotlin_collections_List (*get_categories)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_UiState thiz);
                    libdengyui_kref_kotlin_collections_Map (*get_cats)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_UiState thiz);
                    libdengyui_KInt (*get_currentTab)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_UiState thiz);
                    libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_CatState (*cat)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_UiState thiz, libdengyui_KInt idx);
                    libdengyui_kref_kotlin_collections_List (*component1)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_UiState thiz);
                    libdengyui_KInt (*component2)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_UiState thiz);
                    libdengyui_kref_kotlin_collections_Map (*component3)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_UiState thiz);
                    libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_UiState (*copy)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_UiState thiz, libdengyui_kref_kotlin_collections_List categories, libdengyui_KInt currentTab, libdengyui_kref_kotlin_collections_Map cats);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_UiState thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_UiState thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_UiState thiz);
                  } UiState;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel (*IndexViewModel)(libdengyui_kref_com_dengying_player_data_source_SourceRepository repo, libdengyui_kref_com_dengying_player_data_cache_PageCache cache, libdengyui_kref_com_dengying_player_ui_viewmodel_LruCache detailCache);
                  libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel_UiState (*get_state)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel thiz);
                  void (*loadData)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel thiz, libdengyui_KInt idx);
                  void (*loadMore)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel thiz, libdengyui_KInt idx);
                  void (*onShown)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel thiz, libdengyui_KInt idx);
                  void (*refresh)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel thiz, libdengyui_KInt idx);
                  void (*selectTab)(libdengyui_kref_com_dengying_player_ui_viewmodel_IndexViewModel thiz, libdengyui_KInt idx);
                } IndexViewModel;
                struct {
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams (*PlayerParams)(const char* url, const char* title, libdengyui_KLong vodId, const char* source, const char* vodName, const char* vodPic, const char* vodUri, const char* lineFrom, libdengyui_KInt episodeIndex, const char* episodeName, libdengyui_kref_kotlin_collections_List altUrls, libdengyui_kref_kotlin_collections_List epUrls, libdengyui_kref_kotlin_collections_List epNames, libdengyui_KLong startAtMs, const char* headersJson);
                    libdengyui_kref_kotlin_collections_List (*get_altUrls)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    libdengyui_kref_kotlin_collections_List (*get_epNames)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    libdengyui_kref_kotlin_collections_List (*get_epUrls)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    libdengyui_KInt (*get_episodeIndex)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    const char* (*get_episodeName)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    const char* (*get_headersJson)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    const char* (*get_lineFrom)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    const char* (*get_source)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    libdengyui_KLong (*get_startAtMs)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    const char* (*get_title)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    const char* (*get_url)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    libdengyui_KLong (*get_vodId)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    const char* (*get_vodName)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    const char* (*get_vodPic)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    const char* (*get_vodUri)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    const char* (*component1)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    const char* (*component10)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    libdengyui_kref_kotlin_collections_List (*component11)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    libdengyui_kref_kotlin_collections_List (*component12)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    libdengyui_kref_kotlin_collections_List (*component13)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    libdengyui_KLong (*component14)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    const char* (*component15)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    const char* (*component2)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    libdengyui_KLong (*component3)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    const char* (*component4)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    const char* (*component5)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    const char* (*component6)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    const char* (*component7)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    const char* (*component8)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    libdengyui_KInt (*component9)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams (*copy)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz, const char* url, const char* title, libdengyui_KLong vodId, const char* source, const char* vodName, const char* vodPic, const char* vodUri, const char* lineFrom, libdengyui_KInt episodeIndex, const char* episodeName, libdengyui_kref_kotlin_collections_List altUrls, libdengyui_kref_kotlin_collections_List epUrls, libdengyui_kref_kotlin_collections_List epNames, libdengyui_KLong startAtMs, const char* headersJson);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams thiz);
                  } PlayerParams;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState (*UiState)(const char* title, libdengyui_KBoolean isPlaying, libdengyui_KLong curTime, libdengyui_KLong totalTime, libdengyui_KBoolean prepared, const char* errMsg, const char* status, libdengyui_KBoolean buffering, libdengyui_KBoolean hasVideoTrack, libdengyui_KInt videoW, libdengyui_KInt videoH, libdengyui_KFloat speed, libdengyui_KBoolean showSpeedSheet, libdengyui_kref_kotlin_collections_List variants, const char* selectedVariant, libdengyui_KBoolean showQualitySheet, libdengyui_KInt curEpIndex, libdengyui_KBoolean controlsVisible, libdengyui_KBoolean autoNext, libdengyui_KBoolean dragging, libdengyui_KLong dragValue, libdengyui_KBoolean seeking, libdengyui_KLong seekTarget, libdengyui_KLong seekStartMs, libdengyui_KBoolean castEnabled, const char* castBlockReason, const char* castMsg, libdengyui_kref_com_dengying_player_domain_playback_CastUtil_CastabilityKind castLink);
                    libdengyui_KBoolean (*get_autoNext)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KBoolean (*get_buffering)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    const char* (*get_castBlockReason)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KBoolean (*get_castEnabled)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_kref_com_dengying_player_domain_playback_CastUtil_CastabilityKind (*get_castLink)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    const char* (*get_castMsg)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KBoolean (*get_controlsVisible)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KInt (*get_curEpIndex)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KLong (*get_curTime)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KLong (*get_dragValue)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KBoolean (*get_dragging)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    const char* (*get_errMsg)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KBoolean (*get_hasVideoTrack)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KBoolean (*get_isPlaying)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KBoolean (*get_prepared)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KLong (*get_seekStartMs)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KLong (*get_seekTarget)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KBoolean (*get_seeking)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    const char* (*get_selectedVariant)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KBoolean (*get_showQualitySheet)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KBoolean (*get_showSpeedSheet)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KFloat (*get_speed)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    const char* (*get_status)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    const char* (*get_title)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KLong (*get_totalTime)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_kref_kotlin_collections_List (*get_variants)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KInt (*get_videoH)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KInt (*get_videoW)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    const char* (*component1)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KInt (*component10)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KInt (*component11)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KFloat (*component12)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KBoolean (*component13)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_kref_kotlin_collections_List (*component14)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    const char* (*component15)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KBoolean (*component16)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KInt (*component17)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KBoolean (*component18)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KBoolean (*component19)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KBoolean (*component2)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KBoolean (*component20)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KLong (*component21)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KBoolean (*component22)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KLong (*component23)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KLong (*component24)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KBoolean (*component25)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    const char* (*component26)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    const char* (*component27)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_kref_com_dengying_player_domain_playback_CastUtil_CastabilityKind (*component28)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KLong (*component3)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KLong (*component4)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KBoolean (*component5)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    const char* (*component6)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    const char* (*component7)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KBoolean (*component8)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_KBoolean (*component9)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState (*copy)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz, const char* title, libdengyui_KBoolean isPlaying, libdengyui_KLong curTime, libdengyui_KLong totalTime, libdengyui_KBoolean prepared, const char* errMsg, const char* status, libdengyui_KBoolean buffering, libdengyui_KBoolean hasVideoTrack, libdengyui_KInt videoW, libdengyui_KInt videoH, libdengyui_KFloat speed, libdengyui_KBoolean showSpeedSheet, libdengyui_kref_kotlin_collections_List variants, const char* selectedVariant, libdengyui_KBoolean showQualitySheet, libdengyui_KInt curEpIndex, libdengyui_KBoolean controlsVisible, libdengyui_KBoolean autoNext, libdengyui_KBoolean dragging, libdengyui_KLong dragValue, libdengyui_KBoolean seeking, libdengyui_KLong seekTarget, libdengyui_KLong seekStartMs, libdengyui_KBoolean castEnabled, const char* castBlockReason, const char* castMsg, libdengyui_kref_com_dengying_player_domain_playback_CastUtil_CastabilityKind castLink);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState thiz);
                  } UiState;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel (*PlayerViewModel)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_PlayerParams params, libdengyui_kref_com_dengying_player_data_source_SourceRepository repo, libdengyui_kref_com_dengying_player_data_store_Store store, libdengyui_kref_com_dengying_player_player_PlayerEngineHost engineHost, libdengyui_KBoolean isDebug);
                  libdengyui_kref_com_dengying_player_player_PlayerEngine (*get_engine)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz);
                  libdengyui_KBoolean (*get_showQualityEntry)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz);
                  libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel_UiState (*get_state)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz);
                  void (*applyQuality)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz, const char* url);
                  void (*attachEngine)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz);
                  libdengyui_KLong (*consumePendingResume)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz);
                  libdengyui_KBoolean (*hasEp)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz, libdengyui_KInt offset);
                  void (*onCleared)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz);
                  void (*onDrag)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz, libdengyui_KLong value);
                  void (*onDragEnd)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz);
                  void (*onDragStart)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz);
                  void (*onEngineAttached)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz);
                  void (*onSeekCancel)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz);
                  void (*onSeekCommit)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz);
                  void (*onSeekDrag)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz, libdengyui_KFloat deltaX, libdengyui_KFloat viewWidth);
                  void (*onSeekStart)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz);
                  const char* (*qualityLabel)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz);
                  void (*resolveUrl)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz);
                  void (*retry)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz);
                  void (*scheduleHide)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz);
                  void (*setAutoNext)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz, libdengyui_KBoolean on);
                  void (*setSpeed)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz, libdengyui_KFloat v);
                  void (*switchEpisode)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz, libdengyui_KInt offset);
                  void (*toggleControls)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz);
                  void (*togglePlay)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz);
                  void (*toggleQualitySheet)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz);
                  void (*toggleSpeedSheet)(libdengyui_kref_com_dengying_player_ui_viewmodel_PlayerViewModel thiz);
                } PlayerViewModel;
                struct {
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState (*UiState)(const char* keyword, libdengyui_kref_kotlin_collections_List results, libdengyui_KBoolean searching, libdengyui_KBoolean searched, const char* tip, libdengyui_kref_kotlin_collections_List history, libdengyui_KBoolean showClearConfirm, libdengyui_kref_kotlin_collections_List suggest);
                    libdengyui_kref_kotlin_collections_List (*get_history)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState thiz);
                    const char* (*get_keyword)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState thiz);
                    libdengyui_kref_kotlin_collections_List (*get_results)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState thiz);
                    libdengyui_KBoolean (*get_searched)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState thiz);
                    libdengyui_KBoolean (*get_searching)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState thiz);
                    libdengyui_KBoolean (*get_showClearConfirm)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState thiz);
                    libdengyui_kref_kotlin_collections_List (*get_suggest)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState thiz);
                    const char* (*get_tip)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState thiz);
                    const char* (*component1)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState thiz);
                    libdengyui_kref_kotlin_collections_List (*component2)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState thiz);
                    libdengyui_KBoolean (*component3)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState thiz);
                    libdengyui_KBoolean (*component4)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState thiz);
                    const char* (*component5)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState thiz);
                    libdengyui_kref_kotlin_collections_List (*component6)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState thiz);
                    libdengyui_KBoolean (*component7)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState thiz);
                    libdengyui_kref_kotlin_collections_List (*component8)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState thiz);
                    libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState (*copy)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState thiz, const char* keyword, libdengyui_kref_kotlin_collections_List results, libdengyui_KBoolean searching, libdengyui_KBoolean searched, const char* tip, libdengyui_kref_kotlin_collections_List history, libdengyui_KBoolean showClearConfirm, libdengyui_kref_kotlin_collections_List suggest);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState thiz);
                  } UiState;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel (*SearchViewModel)(libdengyui_kref_com_dengying_player_data_source_SourceRepository repo, libdengyui_kref_com_dengying_player_data_store_Store store);
                  libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel_UiState (*get_state)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel thiz);
                  void (*cancelSearch)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel thiz);
                  void (*consumeTip)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel thiz);
                  void (*dismissClearHistory)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel thiz);
                  void (*doClearHistory)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel thiz);
                  void (*doSearch)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel thiz);
                  void (*loadSuggest)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel thiz, const char* keyword);
                  void (*onDeleteHistory)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel thiz, const char* kw);
                  void (*onKeywordChange)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel thiz, const char* v);
                  void (*reloadHistory)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel thiz);
                  void (*requestClearHistory)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel thiz);
                  void (*searchFromHistory)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel thiz, const char* kw);
                  libdengyui_KInt (*searchSourceCount)(libdengyui_kref_com_dengying_player_ui_viewmodel_SearchViewModel thiz);
                } SearchViewModel;
                struct {
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_Row (*Row)(const char* key, const char* displayName, libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row health, libdengyui_KBoolean enabled, libdengyui_KBoolean changed);
                    libdengyui_KBoolean (*get_changed)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_Row thiz);
                    const char* (*get_displayName)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_Row thiz);
                    libdengyui_KBoolean (*get_enabled)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_Row thiz);
                    libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row (*get_health)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_Row thiz);
                    const char* (*get_key)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_Row thiz);
                    const char* (*component1)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_Row thiz);
                    const char* (*component2)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_Row thiz);
                    libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row (*component3)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_Row thiz);
                    libdengyui_KBoolean (*component4)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_Row thiz);
                    libdengyui_KBoolean (*component5)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_Row thiz);
                    libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_Row (*copy)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_Row thiz, const char* key, const char* displayName, libdengyui_kref_com_dengying_player_domain_source_SourceHealthRules_Row health, libdengyui_KBoolean enabled, libdengyui_KBoolean changed);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_Row thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_Row thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_Row thiz);
                  } Row;
                  struct {
                    libdengyui_KType* (*_type)(void);
                    libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_UiState (*UiState)(libdengyui_kref_kotlin_collections_List rows, libdengyui_KBoolean fallbackAll, libdengyui_KInt overridden, const char* tip);
                    libdengyui_KBoolean (*get_fallbackAll)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_UiState thiz);
                    libdengyui_KInt (*get_overridden)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_UiState thiz);
                    libdengyui_kref_kotlin_collections_List (*get_rows)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_UiState thiz);
                    const char* (*get_tip)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_UiState thiz);
                    libdengyui_kref_kotlin_collections_List (*component1)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_UiState thiz);
                    libdengyui_KBoolean (*component2)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_UiState thiz);
                    libdengyui_KInt (*component3)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_UiState thiz);
                    const char* (*component4)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_UiState thiz);
                    libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_UiState (*copy)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_UiState thiz, libdengyui_kref_kotlin_collections_List rows, libdengyui_KBoolean fallbackAll, libdengyui_KInt overridden, const char* tip);
                    libdengyui_KBoolean (*equals)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_UiState thiz, libdengyui_kref_kotlin_Any other);
                    libdengyui_KInt (*hashCode)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_UiState thiz);
                    const char* (*toString)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_UiState thiz);
                  } UiState;
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel (*SourceStatusViewModel)(libdengyui_kref_com_dengying_player_data_source_SourceRepository repo, libdengyui_kref_com_dengying_player_data_store_Store store);
                  libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel_UiState (*get_state)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel thiz);
                  void (*consumeTip)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel thiz);
                  void (*reload)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel thiz);
                  void (*restoreDefaults)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel thiz);
                  void (*toggle)(libdengyui_kref_com_dengying_player_ui_viewmodel_SourceStatusViewModel thiz, const char* key);
                } SourceStatusViewModel;
                struct {
                  libdengyui_KType* (*_type)(void);
                  libdengyui_kref_com_dengying_player_ui_viewmodel_SuggestDebouncer (*SuggestDebouncer)(libdengyui_kref_kotlinx_coroutines_CoroutineScope scope, libdengyui_KLong delayMs);
                  libdengyui_KBoolean (*get_pending)(libdengyui_kref_com_dengying_player_ui_viewmodel_SuggestDebouncer thiz);
                  void (*cancel)(libdengyui_kref_com_dengying_player_ui_viewmodel_SuggestDebouncer thiz);
                  libdengyui_kref_kotlinx_coroutines_Job (*submit)(libdengyui_kref_com_dengying_player_ui_viewmodel_SuggestDebouncer thiz, libdengyui_kref_kotlin_coroutines_SuspendFunction0 block);
                } SuggestDebouncer;
              } viewmodel;
            } ui;
          } player;
        } dengying;
      } com;
      struct {
        struct {
          struct {
            struct {
              struct {
                void (*InitJsRenderNodeContext_)(void* env, void* nodeConstructor, void* statusModifyConstructor, libdengyui_KDouble ratio, libdengyui_KBoolean fixed);
                void (*_Export_ArkUIViewController_aboutToAppear)(void* controllerRef);
                void (*_Export_ArkUIViewController_aboutToDisappear)(void* controllerRef);
                void (*_Export_ArkUIViewController_cancelSyncRefresh)(void* controllerRef, libdengyui_KInt refreshId);
                libdengyui_KBoolean (*_Export_ArkUIViewController_dispatchComposeKeyEvent)(void* controllerRef, libdengyui_KLong keyCode, libdengyui_KInt nativeAction, libdengyui_KLong modifierKeyStates);
                libdengyui_KBoolean (*_Export_ArkUIViewController_dispatchComposeMouseEvent)(void* controllerRef, void* nativeMouseEvent);
                libdengyui_KBoolean (*_Export_ArkUIViewController_dispatchComposePinchEvent)(void* controllerRef, void* nativePinchEvent);
                void (*_Export_ArkUIViewController_dispatchHoverEvent)(void* controllerRef);
                void (*_Export_ArkUIViewController_dispatchMouseEvent)(void* controllerRef);
                libdengyui_KBoolean (*_Export_ArkUIViewController_dispatchTouchEvent)(void* controllerRef, void* nativeTouchEvent, libdengyui_KBoolean ignoreInteropView);
                void (*_Export_ArkUIViewController_draw)(void* controllerRef, void* canvas);
                libdengyui_KInt (*_Export_ArkUIViewController_findNodeIdAt)(void* controllerRef, libdengyui_KFloat x, libdengyui_KFloat y);
                const char* (*_Export_ArkUIViewController_getId)(void* controllerRef);
                void* (*_Export_ArkUIViewController_getJsNode)(void* controllerRef);
                libdengyui_KInt (*_Export_ArkUIViewController_getRendererTypeId)();
                void* (*_Export_ArkUIViewController_getXComponentRender)(void* controllerRef);
                void (*_Export_ArkUIViewController_initFusionRendererNode)(void* controllerRef, libdengyui_KBoolean enableCApi, void* rootContent, void* frameMgr);
                void (*_Export_ArkUIViewController_initFusionRendererNodePreComposed)(void* controllerRef, libdengyui_KBoolean enableCApi, void* rootContent, void* frameMgr, libdengyui_KBoolean isPreCompose);
                void (*_Export_ArkUIViewController_keyboardWillHide)(void* controllerRef);
                void (*_Export_ArkUIViewController_keyboardWillShow)(void* controllerRef, libdengyui_KFloat keyboardHeight);
                libdengyui_KBoolean (*_Export_ArkUIViewController_onBackPress)(void* controllerRef);
                void (*_Export_ArkUIViewController_onConfigurationUpdate)(void* controllerRef);
                void (*_Export_ArkUIViewController_onFinalize)(void* controllerRef);
                void (*_Export_ArkUIViewController_onFocusEvent)(void* controllerRef);
                void (*_Export_ArkUIViewController_onFrame)(void* controllerRef, libdengyui_KLong timestamp, libdengyui_KLong targetTimestamp);
                void (*_Export_ArkUIViewController_onIdle)(void* controllerRef, libdengyui_KLong timeLeft);
                void (*_Export_ArkUIViewController_onKeyEvent)(void* controllerRef);
                void (*_Export_ArkUIViewController_onPageHide)(void* controllerRef);
                void (*_Export_ArkUIViewController_onPageShow)(void* controllerRef);
                void (*_Export_ArkUIViewController_onSurfaceChanged)(void* controllerRef, libdengyui_KInt width, libdengyui_KInt height);
                void (*_Export_ArkUIViewController_onSurfaceCreated)(void* controllerRef, void* xcomponentPtr, libdengyui_KInt width, libdengyui_KInt height);
                void (*_Export_ArkUIViewController_onSurfaceDestroyed)(void* controllerRef);
                void (*_Export_ArkUIViewController_onSurfaceHide)(void* controllerRef);
                void (*_Export_ArkUIViewController_onSurfaceShow)(void* controllerRef);
                libdengyui_KInt (*_Export_ArkUIViewController_requestSyncRefresh)(void* controllerRef);
                const char* (*_Export_ArkUIViewController_sendMessage)(void* controllerRef, const char* type, const char* message);
                void (*_Export_ArkUIViewController_setContext)(void* controllerRef, void* context);
                void (*_Export_ArkUIViewController_setEnv)(void* controllerRef, void* env);
                void (*_Export_ArkUIViewController_setFusionRendererRootNode)(void* controllerRef, void* rootFrameNode, void* uiContext);
                void (*_Export_ArkUIViewController_setId)(void* controllerRef, const char* id);
                void (*_Export_ArkUIViewController_setLocaleAndStringProvider)(void* controllerRef, void* provider);
                void (*_Export_ArkUIViewController_setMessenger)(void* controllerRef, void* messenger);
                void (*_Export_ArkUIViewController_setRendererBackendId)(void* controllerRef, libdengyui_KInt backendId);
                void (*_Export_ArkUIViewController_setRootView)(void* controllerRef, void* backRootView, void* foreRootView, void* touchableRootView);
                void (*_Export_ArkUIViewController_setUIContext)(void* controllerRef, void* uiContext);
                void (*_Export_ArkUIViewController_setXComponentRender)(void* controllerRef, void* render);
                void (*_Export_LowMemoryMonitor_notifyLowMemoryFromHost)(libdengyui_KInt rawLevel);
                libdengyui_KLong (*getCurrentTimeNanos)();
                libdengyui_KFloat (*_Export_ArkUIViewController_consumeOuterScroll)(void* controllerRef, libdengyui_KFloat offsetVp, libdengyui_KInt sourceInt);
                void (*_Export_ArkUIViewController_setParentScrollBridge)(void* controllerRef, void* scroller);
                void (*_Export_ArkUIViewInitializer_init)(void* env, void* exports);
              } arkui;
            } ui;
          } export_;
        } compose;
      } androidx;
    } root;
  } kotlin;
} libdengyui_ExportedSymbols;
extern libdengyui_ExportedSymbols* libdengyui_symbols(void);
#ifdef __cplusplus
}  /* extern "C" */
#endif
#endif  /* KONAN_LIBDENGYUI_H */

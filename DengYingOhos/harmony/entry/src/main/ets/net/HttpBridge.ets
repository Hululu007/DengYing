import dengying from 'libentry.so';
import { http } from '@kit.NetworkKit';
import { util } from '@kit.ArkTS';
import { hilog } from '@kit.PerformanceAnalysisKit';

const DOMAIN = 0x0000;
const TAG = 'HttpBridge';

/**
 * P6-5：:shared HttpTransport 的 ArkTS 执行器。
 * Kotlin 侧把请求 JSON（{id,method,url,body?,headers,connectMs,readMs,binary}）
 * 经 NAPI tsfn 投到本线程，这里用系统 http 模块真实发请求，完成后把响应
 * base64 回注（submitHttpResult）。失败一律 status=0、b64 空串。
 */
function decodeBase64(b64: string): ArrayBuffer {
  const helper = new util.Base64Helper();
  const bytes = helper.decodeSync(b64);
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
}

function encodeBase64(data: Object): string {
  const helper = new util.Base64Helper();
  if (typeof data === 'string') {
    const textBytes = new util.TextEncoder().encodeInto(data as string);
    return helper.encodeToStringSync(new Uint8Array(textBytes.buffer));
  }
  return helper.encodeToStringSync(new Uint8Array(data as ArrayBuffer));
}

async function runRequest(jsonText: string): Promise<void> {
  hilog.info(DOMAIN, TAG, 'runRequest: %{public}s', jsonText.substring(0, 120));
  let id = 0;
  let binary = false;
  try {
    const req = JSON.parse(jsonText) as Record<string, Object>;
    id = Number(req['id']);
    binary = Boolean(req['binary']);
    const method = String(req['method']) as http.RequestMethod;
    const url = String(req['url']);
    const connectMs = Number(req['connectMs']);
    const readMs = Number(req['readMs']);
    const headers = req['headers'] as Record<string, string>;
    const bodyB64 = req['body'] ? String(req['body']) : '';

    const options: http.HttpRequestOptions = {
      method: method,
      header: headers,
      connectTimeout: connectMs,
      readTimeout: readMs,
      expectDataType: binary ? http.HttpDataType.ARRAY_BUFFER : http.HttpDataType.STRING,
    };
    if (bodyB64.length > 0) {
      options.extraData = decodeBase64(bodyB64);
    }

    const client = http.createHttp();
    try {
      const response = await client.request(url, options);
      // 契约 3：2xx 与错误体都原样带回（ArkTS http 对两种情况都填 result）。
      const payload = response.result ? encodeBase64(response.result) : '';
      dengying.submitHttpResult(id, response.responseCode, binary ? 1 : 0, payload);
    } finally {
      client.destroy();
    }
  } catch (err) {
    hilog.error(DOMAIN, TAG, 'request failed: %{public}s', JSON.stringify(err));
    try {
      dengying.submitHttpResult(id, 0, binary ? 1 : 0, '');
    } catch (inner) {
      hilog.error(DOMAIN, TAG, 'submitHttpResult failed: %{public}s', JSON.stringify(inner));
    }
  }
}

export function registerHttpExecutor(): void {
  dengying.registerHttpExecutor((jsonText: string) => {
    runRequest(jsonText);
  });
  hilog.info(DOMAIN, TAG, '%{public}s', 'http executor registered');
}

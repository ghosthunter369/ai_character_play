// @ts-ignore
/* eslint-disable */
import request from "@/request";

/** 此处后端没有提供注释 GET /chatHistory/app/${param0} */
export async function listAppChatHistory(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listAppChatHistoryParams,
  options?: { [key: string]: any }
) {
  const { appId: param0, ...queryParams } = params;
  return request<API.BaseResponseChatHistoryResponse>(
    `/chatHistory/app/${param0}`,
    {
      method: "GET",
      params: {
        // pageSize has a default value: 10
        pageSize: "10",

        ...queryParams,
      },
      ...(options || {}),
    }
  );
}

/** 此处后端没有提供注释 GET /chatHistory/exportChatHistoryTxt */
export async function exportChatHistory(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.exportChatHistoryParams,
  options?: { [key: string]: any }
) {
  return request<any>("/chatHistory/exportChatHistoryTxt", {
    method: "GET",
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

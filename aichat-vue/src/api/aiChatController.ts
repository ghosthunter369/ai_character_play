// @ts-ignore
/* eslint-disable */
import request from "@/request";

/** 此处后端没有提供注释 GET /chat/chat */
export async function chat(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.chatParams,
  options?: { [key: string]: any }
) {
  return request<API.ServerSentEventString[]>("/chat/chat", {
    method: "GET",
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/** 此处后端没有提供注释 POST /chat/create */
export async function createApp(
  body: API.AppDTO,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseString>("/chat/create", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}

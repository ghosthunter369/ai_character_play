<template>
<section class="voice-chat-box" aria-label="语音对话">
<header><strong>语音对话 · 按轮发言</strong><el-button @click="run(newSession)">新会话</el-button></header>
<div class="providers">
<label>语音识别 <select v-model="asr" :disabled="connected || busy"><option v-for="item in providers.asr" :key="item.id" :value="item.id" :disabled="!item.configured">{{ label(item) }}</option></select></label>
<label>语音输出 <select v-model="tts" :disabled="connected || busy"><option v-for="item in providers.tts" :key="item.id" :value="item.id" :disabled="!item.configured">{{ label(item) }}</option></select></label>
<small>切换供应商前请结束通话。浏览器识别可能需要联网，中文音色由浏览器提供。</small>
</div>
<p v-if="notice" class="notice" role="status">{{ notice }}</p>
<p v-if="error" class="error" role="alert">{{ error }}</p>
<div ref="area" class="messages" role="log" aria-live="polite">
<p v-if="!messages.length" class="empty">{{ prologue || '可以直接输入文字，或开始录音，完成后点击结束发言。' }}</p>
<article v-for="message in messages" :key="message.key" :class="message.type"><small>{{ message.type === 'user' ? '我' : '角色' }}{{ message.streaming ? ' · 正在回复' : '' }}</small><div>{{ message.content }}</div>
<div v-if="message.type === 'ai' && !message.streaming && message.turnId" class="feedback"><span>反馈</span><button v-for="item in feedbackOptions" :key="item.id" :disabled="!connected" @click="feedback(item.id,message.turnId)">{{ item.label }}</button></div>
</article><p v-if="partial" class="partial">正在识别：{{ partial }}</p>
</div>
<div class="status" role="status">{{ status }}<span v-if="latency !== null">首音 {{ latency }} ms（本轮识别完成/文字提交至浏览器开始播放）</span></div>
<div class="controls"><el-button type="primary" :disabled="recording || playing || busy" @click="run(start)">开始录音</el-button><el-button :disabled="!recording || busy" @click="run(() => service.stopRecording())">结束发言</el-button><el-button type="warning" :disabled="!connected" @click="interrupt">打断</el-button><el-button :disabled="!connected || busy" @click="run(() => service.disconnect())">结束通话</el-button></div>
<small v-if="recording" class="hint">单轮最多30秒；播放期间暂停上传，开始新发言前请先打断。</small>
<form class="text-input" @submit.prevent="run(sendText)"><input v-model="draft" aria-label="输入本轮文字" placeholder="也可以输入文字与角色交谈" :disabled="recording" maxlength="4000"><el-button native-type="submit" type="primary" :disabled="!draft.trim() || busy || recording">发送</el-button></form>
</section>
</template>
<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { VoiceChatService, type VoiceChatMessage } from '@/services/voiceChatService'
import request from '@/request'
const props=withDefaults(defineProps<{appId:string|number;wsUrl?:string;autoConnect?:boolean;prologue?:string}>(),{autoConnect:false,prologue:''})
const emit=defineEmits<{message:[message:VoiceChatMessage];connectionChange:[value:boolean];recordingChange:[value:boolean];volumeChange:[volume:number,status:string,info:string]}>()
const service=new VoiceChatService()
const connected=ref(false),recording=ref(false),playing=ref(false),thinking=ref(false),busy=ref(false)
const notice=ref(''),error=ref(''),partial=ref(''),draft=ref(''),latency=ref<number|null>(null),asr=ref('browser'),tts=ref('browser'),area=ref<HTMLElement>()
interface Provider{id:string;configured:boolean}
const providers=ref<{asr:Provider[];tts:Provider[]}>({asr:[{id:'browser',configured:true}],tts:[{id:'browser',configured:true}]})
const messages=ref<(VoiceChatMessage & {key:string;streaming?:boolean})[]>([])
const feedbackOptions=[{id:'latency',label:'延迟较高'},{id:'asr',label:'识别不准'},{id:'relevance',label:'答非所问'},{id:'character',label:'角色不符'},{id:'audio',label:'声音异常'}]
const status=computed(()=>recording.value?'正在录音，完成后点击结束发言':playing.value?'正在播放回复':thinking.value?'正在生成回复':connected.value?'会话已就绪':'发送或录音时自动连接')
const label=(item:Provider)=>`${({browser:'浏览器',dashscope:'DashScope',siliconflow:'SiliconFlow',local:'本地服务',xunfei:'讯飞'} as Record<string,string>)[item.id]||item.id}${item.configured?'':'（未配置）'}`
function upsert(message:VoiceChatMessage,streaming=false){const key=`${message.turnId}:${message.type}`;const existing=messages.value.find(item=>item.key===key);if(existing)Object.assign(existing,message,{streaming});else messages.value.push({...message,key,streaming});void nextTick(()=>{if(area.value)area.value.scrollTop=area.value.scrollHeight})}
service.onMessage=(message:VoiceChatMessage)=>{upsert(message);emit('message',message)}
service.onStreamingMessage=(message:VoiceChatMessage & {isStreaming:boolean})=>upsert(message,message.isStreaming)
service.onConnectionChange=(value:boolean)=>{connected.value=value;emit('connectionChange',value)}
service.onRecordingChange=(value:boolean)=>{recording.value=value;emit('recordingChange',value)}
service.onPlaybackChange=(value:boolean)=>{playing.value=value}
service.onTurnChange=(value:boolean)=>{thinking.value=value}
service.onAsrPartial=(text:string)=>{partial.value=text}
service.onAsrFinal=()=>{partial.value=''}
service.onVolumeChange=(volume:number,status:string,info:string)=>emit('volumeChange',volume,status,info)
service.onError=(message:string)=>{error.value=message}
service.onFeedback=()=>{notice.value='反馈已记录'}
service.onLatency=(value:number)=>{latency.value=value}
async function run(action:()=>unknown){if(busy.value)return;busy.value=true;error.value='';try{await action()}catch(cause){error.value=cause instanceof Error?cause.message:'操作失败，请重试'}finally{busy.value=false}}
async function connect(){service.asrProvider=asr.value;service.ttsProvider=tts.value;await service.connect(props.appId)}
async function start(){await service.unlockAudio();await connect();partial.value='';latency.value=null;await service.startRecording()}
async function sendText(){await service.unlockAudio();await connect();service.submitText(draft.value);draft.value='';partial.value='';latency.value=null}
function interrupt(){service.cancelTurn();partial.value='';for(const item of messages.value)item.streaming=false}
async function newSession(){await service.newConversation();messages.value=[];partial.value='';latency.value=null;thinking.value=false}
function feedback(category:string,turnId:string){try{service.feedback(category,turnId);notice.value='反馈已发送，等待确认'}catch(cause){error.value=(cause as Error).message}}
onMounted(async()=>{try{const response=await request.get('/voice/providers');if(response.data?.code===0&&response.data?.data){const data=response.data.data;providers.value={asr:[{id:'browser',configured:true},...(data.asr||[]).filter((item:Provider)=>item.id!=='browser')],tts:[{id:'browser',configured:true},...(data.tts||[]).filter((item:Provider)=>item.id!=='browser')]}}}catch{error.value='无法读取供应商配置；仍可尝试浏览器输出与文字输入。'}})
onUnmounted(()=>{void service.disconnect()})
defineExpose({connect,disconnect:()=>service.disconnect(),startRecording:start,stopRecording:()=>service.stopRecording(),toggleRecording:()=>service.toggleRecording(props.appId),getConnectionStatus:()=>service.getConnectionStatus(),getRecordingStatus:()=>service.getRecordingStatus(),getStats:()=>service.getStats(),isActive:()=>service.isActive(),connected,recording,loadHistory:()=>Promise.resolve()})
</script>
<style scoped>
.voice-chat-box{display:flex;flex-direction:column;min-height:440px;height:100%;max-height:780px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:16px;overflow:hidden;color:#26344a}header{display:flex;justify-content:space-between;align-items:center;padding:16px 20px;background:white}.providers{display:flex;flex-wrap:wrap;gap:10px 20px;padding:12px 20px;border-top:1px solid #edf0f5}.providers label{display:flex;align-items:center;gap:8px;font-size:13px}.providers select{max-width:190px;border:1px solid #cbd5e1;border-radius:6px;padding:6px;background:white}.providers>small{width:100%;color:#64748b}.messages{flex:1;min-height:140px;overflow-y:auto;padding:16px 20px;background:white}.messages article{max-width:85%;width:fit-content;padding:10px 14px;margin-bottom:14px;border-radius:12px;background:#edf2f8;white-space:pre-wrap;overflow-wrap:anywhere}.messages article.user{margin-left:auto;background:#e4efff}.messages article>small{display:block;color:#64748b;margin-bottom:5px;font-size:11px}.empty{color:#64748b;line-height:1.8}.partial{color:#516a8d;border-left:3px solid #93b9ee;padding-left:10px}.feedback{display:flex;flex-wrap:wrap;align-items:center;gap:6px;margin-top:10px;font-size:11px;color:#64748b}.feedback button{font-size:11px;color:#526781;border:1px solid #ced9e7;background:transparent;border-radius:4px;cursor:pointer;padding:3px 5px}.feedback button:disabled{opacity:.45;cursor:default}.status{display:flex;gap:8px;flex-wrap:wrap;font-size:12px;color:#526781;padding:12px 20px}.controls{display:flex;flex-wrap:wrap;gap:8px;padding:0 20px 12px}.controls .el-button{margin:0}.text-input{display:flex;gap:8px;padding:12px 20px;border-top:1px solid #e2e8f0}.text-input input{flex:1;min-width:0;border:1px solid #cbd5e1;border-radius:7px;padding:10px;font:inherit}.hint{padding:0 20px 8px;color:#64748b}.notice{margin:0;padding:8px 20px;color:#287449;background:#effaf3;font-size:13px}.error{margin:0;padding:10px 20px;color:#9f332c;background:#fff0ed;font-size:13px}@media(max-width:600px){.providers,header,.messages,.status,.text-input{padding-left:12px;padding-right:12px}.controls{padding-left:12px;padding-right:12px}.messages article{max-width:95%}.providers label{width:100%;justify-content:space-between}}
</style>

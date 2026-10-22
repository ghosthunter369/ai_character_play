<template>
  <section class="voice-chat-box" aria-label="语音对话">
    <header class="voice-header">
      <div><strong>语音对话</strong><p>按轮发言，随时打断</p></div>
      <el-button size="small" @click="run(newSession)">新会话</el-button>
    </header>
    <div class="providers">
      <label>识别服务 <select v-model="asr" aria-label="语音识别" :disabled="connected || busy"><option v-for="item in providers.asr" :key="item.id" :value="item.id" :disabled="!item.configured">{{ label(item) }}</option></select></label>
      <label>声音服务 <select v-model="tts" aria-label="语音输出" :disabled="connected || busy"><option v-for="item in providers.tts" :key="item.id" :value="item.id" :disabled="!item.configured">{{ label(item) }}</option></select></label>
      <small>结束通话后可切换服务。浏览器识别可能需要联网。</small>
    </div>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div ref="area" class="messages" role="log" aria-live="polite">
      <div v-if="!messages.length" class="voice-empty">
        <span class="empty-symbol" aria-hidden="true"><el-icon><Microphone /></el-icon></span>
        <h3>{{ prologue || '准备好，就开始聊吧。' }}</h3>
        <p>点击开始录音，说完后选择结束发言。<br>也可以在下方直接输入文字。</p>
      </div>
      <article v-for="message in messages" :key="message.key" :class="message.type">
        <small>{{ message.type === 'user' ? '你' : '角色' }}{{ message.streaming ? ' · 正在回复' : '' }}</small>
        <div>{{ message.content }}</div>
        <div v-if="message.type === 'ai' && !message.streaming && message.turnId" class="feedback">
          <span>反馈</span><button v-for="item in feedbackOptions" :key="item.id" :disabled="!connected" @click="feedback(item.id,message.turnId)">{{ item.label }}</button>
        </div>
      </article>
      <p v-if="partial" class="partial">正在识别：{{ partial }}</p>
    </div>
    <div class="voice-bottom">
      <div class="status" role="status"><span><i :class="{active:connected}" aria-hidden="true"></i>{{ status }}</span><span v-if="latency !== null" title="从本轮识别完成或文字提交，到浏览器实际开始播放；不含录音和识别耗时。">首音 {{ latency }} ms</span></div>
      <div class="controls">
        <el-button type="primary" :disabled="recording || playing || busy" @click="run(start)"><el-icon><Microphone /></el-icon><span>开始录音</span></el-button>
        <el-button :disabled="!recording || busy" @click="run(() => service.stopRecording())">结束发言</el-button>
        <el-button class="interrupt-button" :disabled="!connected" @click="interrupt">打断</el-button>
        <el-button class="disconnect-button" :disabled="!connected || busy" @click="run(() => service.disconnect())">结束通话</el-button>
      </div>
      <small v-if="recording" class="hint">单轮最多30秒。开始新发言前，请先打断当前回复。</small>
      <form class="text-input" @submit.prevent="run(sendText)">
        <input v-model="draft" aria-label="输入本轮文字" placeholder="写下你想聊的，按 Enter 发送…" :disabled="recording" maxlength="2000">
        <el-button native-type="submit" type="primary" :disabled="!draft.trim() || busy || recording">发送</el-button>
      </form>
    </div>
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
const label=(item:Provider)=>`${({browser:'浏览器',dashscope:'DashScope',siliconflow:'SiliconFlow',local:'本地服务',azure:'Azure',xunfei:'讯飞'} as Record<string,string>)[item.id]||item.id}${item.configured?'':'（未配置）'}`
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
.voice-chat-box {height:100%;display:flex;flex-direction:column;min-height:0;background:white;min-width:0}
.voice-header {display:flex;align-items:center;justify-content:space-between;gap:16px;padding:22px 32px 16px}
.voice-header strong {font-size:14px;font-weight:600}.voice-header p {font-size:11px;color:var(--ui-muted);margin-top:2px}
.providers {display:flex;align-items:center;flex-wrap:wrap;gap:12px 24px;padding:0 32px 18px;border-bottom:1px solid #edf0e7}
.providers label {display:flex;align-items:center;gap:8px;font-size:11px;color:var(--ui-muted)}
.providers select {max-width:185px;border:1px solid #dfe5d9;border-radius:6px;padding:5px 24px 5px 8px;color:#44523f;background:#fafbf8;font-size:12px;min-width:105px}
.providers>small {font-size:10px;color:var(--ui-muted)}
.messages {flex:1;min-height:80px;overflow-y:auto;padding:24px 32px}
.messages article {max-width:min(85%,720px);width:fit-content;padding:14px 18px;margin-bottom:22px;border-radius:10px;background:#f4f6f0;border:1px solid #e8ede2;white-space:pre-wrap;overflow-wrap:anywhere;font-size:14px;line-height:1.85}
.messages article.user {margin-left:auto;background:#eaf1e5;border-color:#dfe8d8}
.messages article>small {display:block;color:var(--ui-muted);margin-bottom:6px;font-size:10px;line-height:1.5}
.voice-empty {min-height:100%;max-width:440px;margin:0 auto;display:flex;flex-direction:column;justify-content:center;align-items:center;text-align:center;padding:20px 0}
.empty-symbol {display:grid;place-items:center;width:44px;height:44px;background:#f0f3e9;border:1px solid #e2e8d9;border-radius:12px;color:#71856a;font-size:20px;margin-bottom:20px}
.voice-empty h3 {font-size:18px;font-weight:500;line-height:1.8;color:#44533e;letter-spacing:-.2px}.voice-empty p {font-size:12px;color:var(--ui-muted);margin-top:12px;line-height:1.9}
.partial {color:var(--ui-accent);border-left:2px solid #a9ba9e;padding-left:12px;font-size:13px}
.feedback {display:flex;flex-wrap:wrap;align-items:center;gap:6px;margin-top:14px;font-size:10px;color:var(--ui-muted);line-height:1.5}
.feedback button {font-size:10px;color:#61745a;border:1px solid #dbe3d4;background:transparent;border-radius:4px;cursor:pointer;padding:3px 6px}
.feedback button:hover {background:white;border-color:#97ae8b}.feedback button:disabled {opacity:.5;cursor:default}
.voice-bottom {padding:16px 32px 22px;border-top:1px solid #edf0e7;background:#fcfdfa}
.status {display:flex;justify-content:space-between;gap:10px;flex-wrap:wrap;font-size:11px;color:var(--ui-muted);margin-bottom:14px}.status>span:first-child {display:flex;align-items:center;gap:7px}
.status i {width:5px;height:5px;border-radius:50%;background:#bac2b4;display:inline-block}.status i.active {background:#65815c}
.controls {display:flex;flex-wrap:wrap;gap:8px;margin-bottom:14px}.controls .el-button {margin:0;font-size:12px;height:34px}.controls .disconnect-button {margin-left:auto;background:transparent;border-color:transparent;color:var(--ui-muted)}
.controls .interrupt-button:not(:disabled) {color:#8c6a3e;background:#faf5e9;border-color:#e8dcc7}
.text-input {display:flex;gap:10px;padding:8px 10px;border:1px solid #dce3d6;border-radius:8px;background:white}
.text-input input {flex:1;min-width:0;border:0;background:transparent;padding:2px 4px;font-size:13px;color:var(--ui-text)}
.text-input input::placeholder {color:#7a8377}.text-input .el-button {height:32px;font-size:12px}
.hint {display:block;font-size:11px;color:var(--ui-muted);margin-bottom:12px}
.notice,.error {margin:0;padding:9px 32px;font-size:12px}.notice {color:#486650;background:#f0f5eb}.error {color:#a14f43;background:#fcf1ec}
@media(max-width:760px) {
 .voice-header {padding:18px 20px 14px}.providers {padding:0 20px 14px;gap:10px 16px}.providers>small {width:100%}
 .providers label {flex:1;min-width:120px;justify-content:space-between}.providers select {max-width:140px;min-width:90px}
 .messages {padding:20px 16px}.messages article {max-width:96%;font-size:13px}.voice-bottom {padding:12px 16px 16px}
 .voice-empty h3 {font-size:16px}.voice-empty {padding:16px 8px}.empty-symbol {width:36px;height:36px;margin-bottom:12px}
 .notice,.error {padding:8px 20px}.controls {gap:6px}.controls .el-button {font-size:11px;padding:7px 10px}
}
</style>

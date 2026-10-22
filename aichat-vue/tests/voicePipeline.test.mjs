import test from 'node:test'
import assert from 'node:assert/strict'
import { VoiceChatService } from '../src/services/voicePipeline.mjs'
class Socket {
  static instances = []
  readyState = 1
  sent = []
  constructor(url) { this.url = url; Socket.instances.push(this) }
  send(value) { this.sent.push(value) }
  close() { this.readyState = 3; this.onclose?.() }
  event(data) { this.onmessage({ data: JSON.stringify(data) }) }
}
function create(extra = {}) {
  return new VoiceChatService({ WebSocket: Socket, location: { protocol: 'https:', host: 'example.test' }, performance: { now: () => 100 }, ...extra })
}
async function ready(service) {
  const promise = service.connect(7)
  const socket = Socket.instances.at(-1)
  socket.onopen?.()
  assert.equal(service.getConnectionStatus(), false)
  socket.event({type: 'session.ready'})
  await promise
  return socket
}
test('session.ready gates commands and websocket uses same origin/session/providers', async () => {
  const service = create()
  assert.throws(() => service.submitText('hello'), /连接/)
  const socket = await ready(service)
  assert.match(socket.url, /^wss:\/\/example.test\/api\/ws\/voice\?/)
  assert.match(socket.url, /conversationId=/)
  service.submitText('hello')
  assert.deepEqual(JSON.parse(socket.sent.at(-1)), {type: 'text.submit', text: 'hello',requestId:service.currentRequestId})
  await service.disconnect()
})
test('real handler streams once and drops cancelled and previous turn events', async () => {
  const service = create(); const socket = await ready(service); const messages = []; const streaming = []
  service.onMessage = message => messages.push(message)
  service.onStreamingMessage = message => streaming.push(message)
  service.submitText('input')
  socket.event({type:'turn.started',turnId:'one',seq:0,requestId:service.currentRequestId})
  socket.event({type:'asr.final',turnId:'one',seq:1,text:'你好'})
  socket.event({type:'reply.delta',turnId:'one',seq:2,text:'你'})
  socket.event({type:'reply.delta',turnId:'one',seq:3,text:'好'})
  socket.event({type:'reply.done',turnId:'one',seq:4,text:'你好'})
  assert.equal(messages.length, 2)
  assert.equal(streaming.at(-1).content, '你好')
  service.cancelTurn()
  socket.event({type:'reply.done',turnId:'one',seq:5,text:'迟到'})
  service.submitText('next')
  socket.event({type:'turn.started',turnId:'two',seq:0,requestId:service.currentRequestId})
  socket.event({type:'reply.delta',turnId:'one',seq:6,text:'旧'})
  socket.event({type:'reply.delta',turnId:'two',seq:1,text:'新'})
  assert.equal(messages.length, 2)
  assert.equal(streaming.at(-1).content, '新')
  await service.disconnect()
})
test('cancel prevents asynchronously decoded old audio from starting', async () => {
  let finishDecode; let starts = 0; let cancels = 0
  class Context {
    state = 'running'; destination = {}
    decodeAudioData() { return new Promise(resolve => { finishDecode = resolve }) }
    createBufferSource() { return {connect(){},start(){starts++},stop(){},disconnect(){}} }
    close() { return Promise.resolve() }
  }
  const service = create({ AudioContext: Context, atob: () => 'audio', speechSynthesis: {cancel(){cancels++}} })
  const socket = await ready(service)
  service.submitText('input')
  socket.event({type:'turn.started',turnId:'one',requestId:service.currentRequestId})
  socket.event({type:'audio.chunk',turnId:'one',data:'abc',mimeType:'audio/wav'})
  await Promise.resolve()
  service.cancelTurn()
  finishDecode({})
  await Promise.resolve(); await Promise.resolve()
  assert.equal(starts, 0)
  assert.ok(cancels > 0)
  await service.disconnect()
})
test('turn.done leaves playback active until actual onended', async () => {
  let source
  class Context {
    state='running'; destination={}
    async decodeAudioData() { return {} }
    createBufferSource() { source = {connect(){},start(){},stop(){},disconnect(){}}; return source }
    close() { return Promise.resolve() }
  }
  const service = create({AudioContext:Context,atob:()=> 'audio'})
  const socket=await ready(service)
  service.submitText('input')
  socket.event({type:'turn.started',turnId:'one',requestId:service.currentRequestId})
  socket.event({type:'audio.chunk',turnId:'one',data:'abc'})
  await Promise.resolve(); await Promise.resolve(); await Promise.resolve()
  socket.event({type:'turn.done',turnId:'one'})
  assert.equal(service.getPlaybackStatus(),true)
  source.onended()
  await Promise.resolve(); await Promise.resolve()
  assert.equal(service.getPlaybackStatus(),false)
  await service.disconnect()
})

test('cancellation before a queued turn.started prevents the stale turn from reviving',async()=>{
  const service=create();const socket=await ready(service);const messages=[]
  service.onMessage=message=>messages.push(message)
  service.submitText('first');service.cancelTurn()
  socket.event({type:'turn.started',turnId:'late'})
  socket.event({type:'reply.done',turnId:'late',text:'must not appear'})
  assert.equal(messages.length,0)
  service.submitText('next')
  socket.event({type:'turn.started',turnId:'next',requestId:service.currentRequestId})
  socket.event({type:'reply.done',turnId:'next',text:'accepted'})
  assert.equal(messages.length,1)
  await service.disconnect()
})
test('disconnect invalidates pending microphone permission and stops returned tracks',async()=>{
  let allow;let stopped=0
  const service=create({navigator:{mediaDevices:{getUserMedia(){return new Promise(resolve=>{allow=resolve})}}}})
  service.asrProvider='local';await ready(service)
  const recording=service.startRecording()
  await service.disconnect()
  allow({getTracks:()=>[{stop(){stopped++}}]})
  await recording
  assert.equal(stopped,1);assert.equal(service.getRecordingStatus(),false)
})
test('browser capability failure remains explicit and text submission still works',async()=>{
  const service=create();const socket=await ready(service)
  await assert.rejects(service.startRecording(),/不支持语音识别/)
  service.submitText('text fallback')
  assert.equal(JSON.parse(socket.sent.at(-1)).type,'text.submit')
  await service.disconnect()
})


test('microphone sends 16k PCM16 only when not playing and commit does not disconnect',async()=>{
  let processor;let stopped=0
  class Context {
    state='running';sampleRate=48000;destination={}
    createMediaStreamSource(){return {connect(){},disconnect(){}}}
    createScriptProcessor(){processor={connect(){},disconnect(){}};return processor}
    async close(){}
  }
  const service=create({AudioContext:Context,navigator:{mediaDevices:{async getUserMedia(){return {getTracks:()=>[{stop(){stopped++}}]}}}}})
  service.asrProvider='local';const socket=await ready(service)
  await service.startRecording()
  processor.onaudioprocess({inputBuffer:{getChannelData:()=>new Float32Array([1,1,1,-1,-1,-1])}})
  const audio=socket.sent.at(-1)
  assert.ok(audio instanceof ArrayBuffer)
  assert.deepEqual([...new Int16Array(audio)],[32767,-32768])
  const count=socket.sent.length;service.setPlaying(true)
  processor.onaudioprocess({inputBuffer:{getChannelData:()=>new Float32Array([1,1,1])}})
  assert.equal(socket.sent.length,count)
  service.setPlaying(false);await service.stopRecording()
  assert.deepEqual(JSON.parse(socket.sent.at(-1)),{type:'audio.commit',requestId:service.currentRequestId})
  assert.equal(service.getConnectionStatus(),true);assert.equal(stopped,1)
  await service.disconnect()
})
test('new conversation changes identity and disconnect stops current source and pending queue',async()=>{
  let source;let starts=0;let stops=0
  class Context{
    state='running';destination={}
    async decodeAudioData(){return {}}
    createBufferSource(){source={connect(){},disconnect(){},start(){starts++},stop(){stops++}};return source}
    async close(){}
  }
  const service=create({AudioContext:Context,atob:()=> 'audio'});const socket=await ready(service);const previous=service.conversationId
  service.submitText('input')
  socket.event({type:'turn.started',turnId:'one',requestId:service.currentRequestId})
  socket.event({type:'audio.chunk',turnId:'one',data:'first'})
  socket.event({type:'audio.chunk',turnId:'one',data:'second'})
  await Promise.resolve();await Promise.resolve()
  await service.newConversation()
  assert.notEqual(service.conversationId,previous);assert.equal(service.getPlaybackStatus(),false)
  assert.equal(starts,1);assert.equal(stops,1);assert.equal(source.onended,null)
})

test('starting a new recording cancels an unfinished reply before accepting more audio',async()=>{
  class Recognition {start(){} abort(){} }
  const service=create({SpeechRecognition:Recognition});const socket=await ready(service);const messages=[]
  service.onMessage=message=>messages.push(message)
  service.submitText('input')
  socket.event({type:'turn.started',turnId:'old',requestId:service.currentRequestId})
  await service.startRecording()
  socket.event({type:'reply.done',turnId:'old',text:'must be dropped'})
  assert.equal(messages.length,0)
  assert.ok(socket.sent.some(value=>typeof value==='string'&&JSON.parse(value).type==='turn.cancel'))
  await service.disconnect()
})
test('browser recognition submits final recognized text when recognition ends naturally',async()=>{
  let recognition
  class Recognition { constructor(){recognition=this} start(){} abort(){} }
  const service=create({SpeechRecognition:Recognition});const socket=await ready(service)
  await service.startRecording()
  recognition.onresult({results:[Object.assign([{transcript:'自然结束'}],{isFinal:true})]})
  recognition.onend()
  assert.deepEqual(JSON.parse(socket.sent.at(-1)),{type:'text.submit',text:'自然结束',requestId:service.currentRequestId})
  await service.disconnect()
})

test('cancelling while microphone context closes cannot submit an abandoned recording',async()=>{
  let finishClose
  class Context {
    state='running';sampleRate=48000;destination={}
    createMediaStreamSource(){return {connect(){},disconnect(){}}}
    createScriptProcessor(){return {connect(){},disconnect(){}}}
    close(){return new Promise(resolve=>{finishClose=resolve})}
  }
  const service=create({AudioContext:Context,navigator:{mediaDevices:{async getUserMedia(){return {getTracks:()=>[{stop(){}}]}}}}})
  service.asrProvider='local';const socket=await ready(service);await service.startRecording()
  const committing=service.stopRecording();service.cancelTurn();finishClose();await committing
  assert.equal(socket.sent.some(value=>typeof value==='string'&&JSON.parse(value).type==='audio.commit'),false)
  await service.disconnect()
})
test('feedback acknowledgement keeps the original turn identity without creating a message',async()=>{
  const service=create();const socket=await ready(service);const saved=[];const messages=[]
  service.onFeedback=(category,turnId)=>saved.push({category,turnId});service.onMessage=message=>messages.push(message)
  service.feedback('audio','older-turn')
  assert.deepEqual(JSON.parse(socket.sent.at(-1)),{type:'feedback',category:'audio',turnId:'older-turn'})
  socket.event({type:'feedback.saved',category:'audio',turnId:'older-turn'})
  assert.deepEqual(saved,[{category:'audio',turnId:'older-turn'}]);assert.equal(messages.length,0)
  await service.disconnect()
})

test('cancelling while the microphone context resumes releases resources without recording',async()=>{
  let resume;let stopped=0
  class Context {
    state='suspended';sampleRate=48000;destination={}
    resume(){return new Promise(resolve=>{resume=resolve})}
    async close(){this.state='closed'}
    createMediaStreamSource(){return {connect(){},disconnect(){}}}
    createScriptProcessor(){return {connect(){},disconnect(){}}}
  }
  const service=create({AudioContext:Context,navigator:{mediaDevices:{async getUserMedia(){return {getTracks:()=>[{stop(){stopped++}}]}}}}})
  service.asrProvider='local';await ready(service)
  const starting=service.startRecording();await Promise.resolve();service.cancelTurn();resume();await starting
  assert.equal(service.getRecordingStatus(),false);assert.equal(stopped,1)
  await service.disconnect()
})

test('old queued turn.started cannot revive after cancel followed immediately by a new submission',async()=>{
  const spoken=[];const messages=[]
  class Utterance {constructor(text){this.text=text}}
  const service=create({SpeechSynthesisUtterance:Utterance,speechSynthesis:{cancel(){},speak(utterance){spoken.push(utterance.text)}}})
  const socket=await ready(service);service.onMessage=message=>messages.push(message)
  service.submitText('old input')
  const oldRequest=JSON.parse(socket.sent.at(-1)).requestId
  service.cancelTurn()
  service.submitText('new input')
  const newRequest=JSON.parse(socket.sent.at(-1)).requestId
  socket.event({type:'turn.started',turnId:'old',requestId:oldRequest})
  socket.event({type:'asr.final',turnId:'old',text:'old transcript'})
  socket.event({type:'reply.sentence',turnId:'old',text:'old audio'})
  assert.equal(messages.length,0)
  assert.deepEqual(spoken,[])
  assert.match(newRequest,/^[0-9a-f-]{36}$/)
  assert.notEqual(oldRequest,newRequest)
  socket.event({type:'turn.started',turnId:'new',requestId:newRequest})
  socket.event({type:'asr.final',turnId:'new',text:'new transcript'})
  assert.deepEqual(messages.map(message=>message.content),['new transcript'])
  await service.disconnect()
})


test('turn.started without a matching requestId is rejected even before any cancellation',async()=>{
  const service=create();const socket=await ready(service);const messages=[]
  service.onMessage=message=>messages.push(message);service.submitText('input')
  const requestId=JSON.parse(socket.sent.at(-1)).requestId
  socket.event({type:'turn.started',turnId:'missing-id'})
  socket.event({type:'asr.final',turnId:'missing-id',text:'missing id'})
  socket.event({type:'turn.started',turnId:'wrong-id',requestId:globalThis.crypto.randomUUID()})
  socket.event({type:'asr.final',turnId:'wrong-id',text:'wrong id'})
  assert.equal(messages.length,0)
  socket.event({type:'turn.started',turnId:'correct',requestId})
  socket.event({type:'asr.final',turnId:'correct',text:'accepted'})
  assert.equal(messages.length,1)
  await service.newConversation();assert.equal(service.currentRequestId,null)
})

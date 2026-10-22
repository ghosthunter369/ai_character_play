import type { VoiceChatMessage } from './voiceChatService'
export class VoiceChatService {
  constructor(env?: typeof globalThis)
  conversationId: string
  currentRequestId: string | null
  turnId: string | null
  asrProvider: string
  ttsProvider: string
  onFeedback?: (category:string,turnId:string) => void
  onMessage?: (message: VoiceChatMessage) => void
  onStreamingMessage?: (message: VoiceChatMessage & {isStreaming:boolean}) => void
  onConnectionChange?: (connected:boolean) => void
  onRecordingChange?: (recording:boolean) => void
  onPlaybackChange?: (playing:boolean) => void
  onTurnChange?: (busy:boolean,turnId:string|null) => void
  onAsrPartial?: (text:string) => void
  onAsrFinal?: (text:string) => void
  onVolumeChange?: (volume:number,status:string,info:string) => void
  onError?: (message:string) => void
  onLatency?: (elapsedMs:number) => void
  connect(appId:string|number):Promise<void>
  disconnect():Promise<void>
  newConversation():Promise<void>
  unlockAudio():Promise<void>
  submitText(text:string):void
  startRecording():Promise<void>
  stopRecording(commit?:boolean):Promise<void>
  cancelTurn():void
  feedback(category:string,turnId?:string):void
  toggleRecording(appId:string|number):Promise<boolean>
  getConnectionStatus():boolean
  getRecordingStatus():boolean
  getPlaybackStatus():boolean
  getStats():Record<string,unknown>
  isActive():boolean
}
export function resamplePCM16(input:Float32Array,sampleRate:number):Int16Array

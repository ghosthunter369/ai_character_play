export { VoiceChatService } from './voicePipeline.mjs'
import { VoiceChatService } from './voicePipeline.mjs'
export interface VoiceChatMessage {
  type: 'user' | 'ai'
  content: string
  timestamp: number
  turnId?: string
}
export default new VoiceChatService()

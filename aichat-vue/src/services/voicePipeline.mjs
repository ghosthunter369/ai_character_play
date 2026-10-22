export class VoiceChatService {
  constructor(env = globalThis) {
    this.env = env; this.conversationId = globalThis.crypto.randomUUID(); this.asrProvider = 'browser'; this.ttsProvider = 'browser'
    this.currentRequestId = null; this.connected = false; this.recording = false; this.playing = false; this.generation = 0; this.cancelled = new Set(); this.audioQueue = []; this.turnId = null; this.reply = ''
  }
  now() { return this.env.performance.now() }
  send(command) {
    if (!this.connected || this.socket?.readyState !== 1) throw new Error('请先连接语音会话')
    this.socket.send(JSON.stringify(command))
  }
  connect(appId) {
    if (this.connected) return Promise.resolve()
    if (this.connecting) return this.connecting
    this.currentRequestId = null
    const url = new URL(`${this.env.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${this.env.location.host}/api/ws/voice`)
    for (const [key, value] of Object.entries({ appId, conversationId: this.conversationId, asrProvider: this.asrProvider, ttsProvider: this.ttsProvider })) url.searchParams.set(key, String(value))
    const socket = this.socket = new this.env.WebSocket(url.toString())
    this.connecting = new Promise((resolve, reject) => {
      let settled = false
      const fail = message => { if (!settled) { settled = true; clearTimeout(timer); this.connecting = null; reject(new Error(message)) } }
      const timer = setTimeout(() => { fail('连接超时，请检查登录和供应商配置'); socket.close() }, 10000)
      socket.onmessage = event => {
        if (this.socket !== socket) return
        let data
        try { data = JSON.parse(event.data) } catch { this.onError?.('语音服务返回了无效消息'); return }
        if (data.type === 'session.ready') { clearTimeout(timer); settled = true; this.connected = true; this.connecting = null; this.onConnectionChange?.(true); resolve(); return }
        if (data.type === 'error' && !settled) { fail(data.message || '会话初始化失败'); socket.close(); return }
        this.handleEvent(data)
      }
      socket.onerror = () => { fail('语音连接失败，请检查登录状态'); this.onError?.('语音连接失败') }
      socket.onclose = () => {
        fail('语音连接已关闭')
        if (this.socket !== socket) return
        this.socket = null; this.connected = false; this.connecting = null; this.cancelLocal(); void this.stopRecording(false); this.onConnectionChange?.(false)
      }
    })
    return this.connecting
  }
  handleEvent(event) {
    if (event.type === 'feedback.saved') { this.onFeedback?.(event.category, event.turnId); return }
    if (event.type === 'turn.started') {
      if (!this.currentRequestId || event.requestId !== this.currentRequestId || this.cancelled.has(event.turnId) || event.turnId === this.turnId) return
      if (this.turnId) this.cancelled.add(this.turnId)
      this.stopPlayback(); this.turnId = event.turnId; this.reply = ''; this.lastSeq = -1; this.userSent = false; this.replySent = false; this.firstPlayback = false
      this.startedAt ??= this.now(); this.onTurnChange?.(true, this.turnId); return
    }
    if (event.type === 'error' && !event.turnId) { this.onError?.(event.message || '语音服务错误'); return }
    if (!event.turnId || event.turnId !== this.turnId || this.cancelled.has(event.turnId)) return
    if (typeof event.seq === 'number') { if (event.seq <= this.lastSeq) return; this.lastSeq = event.seq }
    const message = (type, content) => ({type, content, timestamp: Date.now(), turnId: this.turnId})
    switch (event.type) {
      case 'asr.partial': this.onAsrPartial?.(event.text || ''); break
      case 'asr.final':
        this.startedAt = this.now(); this.onAsrFinal?.(event.text || '')
        if (!this.userSent) { this.userSent = true; this.onMessage?.(message('user', event.text || '')) }
        break
      case 'reply.delta': this.reply += event.text || ''; this.onStreamingMessage?.({...message('ai', this.reply), isStreaming: true}); break
      case 'reply.done':
        this.reply = event.text || this.reply; this.onStreamingMessage?.({...message('ai', this.reply), isStreaming: false})
        if (!this.replySent) { this.replySent = true; this.onMessage?.(message('ai', this.reply)) }
        break
      case 'reply.sentence': if (this.ttsProvider === 'browser') this.enqueue({text: event.text}); break
      case 'audio.chunk': this.enqueue({data: event.data, mimeType: event.mimeType}); break
      case 'turn.done': this.onTurnChange?.(false, this.turnId); break
      case 'turn.cancelled': this.cancelLocal(); break
      case 'error': this.onError?.(event.message || '语音服务错误，文字回复仍可保留'); break
    }
  }
  submitText(text) {
    text = text.trim(); if (!text) return
    if (this.turnId) this.cancelTurn()
    this.currentRequestId = globalThis.crypto.randomUUID(); this.startedAt = this.now(); this.send({type: 'text.submit', text, requestId: this.currentRequestId})
  }
  cancelLocal() { this.currentRequestId = null; if (this.turnId) this.cancelled.add(this.turnId); this.stopPlayback(); this.startedAt = null; this.onTurnChange?.(false, this.turnId) }
  cancelTurn() { if (this.connected) this.send({type:'turn.cancel'}); this.cancelLocal(); void this.stopRecording(false) }
  stopPlayback() {
    this.generation++; this.audioQueue = []; this.draining = false; this.env.speechSynthesis?.cancel()
    if (this.activeSource) { this.activeSource.onended = null; try { this.activeSource.stop() } catch {} this.activeSource.disconnect(); this.activeSource = null }
    this.finishPlayback?.(); this.finishPlayback = null; this.setPlaying(false)
  }
  setPlaying(value) { this.playing = value; this.onPlaybackChange?.(value) }
  enqueue(item) { this.audioQueue.push(item); void this.drainAudio() }
  playbackStarted() {
    if (this.firstPlayback) return
    this.firstPlayback = true
    if (this.startedAt != null) { const elapsedMs = Math.max(0, Math.round(this.now() - this.startedAt)); this.onLatency?.(elapsedMs); if (this.connected) this.send({type:'playback.started', turnId:this.turnId, elapsedMs}) }
  }
  async drainAudio() {
    if (this.draining) return
    const generation = this.generation; this.draining = true; this.setPlaying(true)
    try {
      while (this.audioQueue.length && generation === this.generation) {
        const item = this.audioQueue.shift()
        if (item.text) {
          if (!this.env.speechSynthesis || !this.env.SpeechSynthesisUtterance) throw new Error('当前浏览器不支持语音合成，请阅读文字回复或选择其他语音输出')
          await new Promise((resolve, reject) => {
            this.finishPlayback = resolve
            const utterance = new this.env.SpeechSynthesisUtterance(item.text)
            utterance.lang = 'zh-CN'; utterance.onstart = () => { if (generation === this.generation) this.playbackStarted() }; utterance.onend = resolve
            utterance.onerror = event => { if (generation !== this.generation || event.error === 'canceled' || event.error === 'interrupted') resolve(); else reject(new Error('浏览器语音播放失败，请检查音频权限')) }
            this.env.speechSynthesis.speak(utterance)
          })
        } else if (item.data) {
          this.playbackContext ??= new this.env.AudioContext()
          const context = this.playbackContext
          if (context.state === 'suspended') await context.resume()
          const binary = this.env.atob(item.data); const bytes = Uint8Array.from(binary, c => c.charCodeAt(0))
          const buffer = await context.decodeAudioData(bytes.buffer)
          if (generation !== this.generation) return
          await new Promise(resolve => {
            this.finishPlayback = resolve
            const source = this.activeSource = context.createBufferSource(); source.buffer = buffer; source.connect(context.destination)
            source.onended = () => { source.disconnect(); if (this.activeSource === source) this.activeSource = null; resolve() }
            source.start(); this.playbackStarted()
          })
        }
      }
    } catch (error) { if (generation === this.generation) this.onError?.(error.message || '语音播放失败，文字回复仍可阅读') }
    finally { if (generation === this.generation) { this.draining = false; this.finishPlayback = null; this.setPlaying(false) } }
  }
  async unlockAudio() { if (this.env.AudioContext) { this.playbackContext ??= new this.env.AudioContext(); if (this.playbackContext.state === 'suspended') await this.playbackContext.resume() } }
  async startRecording() {
    if (!this.connected) throw new Error('请先连接语音会话')
    if (this.recording) return
    if (this.playing) throw new Error('请先打断语音回复，再开始录音')
    if (this.currentRequestId || (this.turnId && !this.cancelled.has(this.turnId))) this.cancelTurn()
    const generation = this.generation
    if (this.asrProvider === 'browser') {
      const Recognition = this.env.SpeechRecognition || this.env.webkitSpeechRecognition
      if (!Recognition) throw new Error('当前浏览器不支持语音识别，请使用文字输入或配置其他识别服务')
      const recognition = this.recognition = new Recognition(); this.browserText = ''; this.commitBrowser = true
      recognition.lang = 'zh-CN'; recognition.interimResults = true; recognition.continuous = true
      recognition.onresult = event => {
        if (this.recognition !== recognition) return
        let final = ''; let partial = ''
        for (let i = 0; i < event.results.length; i++) { const result = event.results[i]; if (result.isFinal) final += result[0].transcript; else partial += result[0].transcript }
        this.browserText = final + partial; this.onAsrPartial?.(this.browserText)
      }
      recognition.onerror = event => { if (event.error !== 'aborted') this.onError?.(`浏览器识别失败：${event.error}。可改用文字输入。`) }
      recognition.onend = () => {
        if (this.recognition !== recognition) return
        this.recognition = null; clearTimeout(this.recordTimer); this.setRecording(false)
        if (this.commitBrowser && this.browserText.trim()) this.submitText(this.browserText)
      }
      recognition.start(); this.setRecording(true)
    } else {
      if (!this.env.navigator?.mediaDevices?.getUserMedia) throw new Error('麦克风需要 HTTPS 或 localhost，请使用文字输入')
      const stream = await this.env.navigator.mediaDevices.getUserMedia({audio:{channelCount:1,echoCancellation:true,noiseSuppression:true}})
      if (!this.connected || generation !== this.generation) { stream.getTracks().forEach(track => track.stop()); return }
      this.stream = stream; this.recordContext = new this.env.AudioContext()
      if (this.recordContext.state === 'suspended') await this.recordContext.resume()
      if (!this.connected || generation !== this.generation) return
      this.source = this.recordContext.createMediaStreamSource(stream); this.processor = this.recordContext.createScriptProcessor(4096, 1, 1); this.samplesSent = 0
      this.processor.onaudioprocess = event => {
        if (!this.recording || !this.connected || this.playing) return
        const input = event.inputBuffer.getChannelData(0); const pcm = resamplePCM16(input, this.recordContext.sampleRate)
        this.samplesSent += pcm.length
        if (this.samplesSent > 16000 * 30) { void this.stopRecording(); this.onError?.('单轮录音最多30秒，已自动提交'); return }
        this.socket.send(pcm.buffer)
        const rms = Math.sqrt(input.reduce((sum,value) => sum + value * value, 0) / input.length)
        this.onVolumeChange?.(rms, 'speech', '录音中')
      }
      this.source.connect(this.processor); this.processor.connect(this.recordContext.destination); this.setRecording(true)
    }
    this.recordTimer = setTimeout(() => { void this.stopRecording() }, 30000)
  }
  setRecording(value) { this.recording = value; this.onRecordingChange?.(value) }
  async stopRecording(commit = true) {
    clearTimeout(this.recordTimer)
    const generation = this.generation
    const wasRecording = this.recording; this.setRecording(false)
    if (this.recognition) {
      this.commitBrowser = commit
      if (commit) this.recognition.stop(); else { const recognition = this.recognition; this.recognition = null; recognition.abort() }
    }
    this.processor?.disconnect(); this.source?.disconnect(); this.processor = null; this.source = null
    this.stream?.getTracks().forEach(track => track.stop()); this.stream = null
    const context = this.recordContext; this.recordContext = null
    if (context && context.state !== 'closed') await context.close()
    if (generation === this.generation && wasRecording && commit && this.asrProvider !== 'browser' && this.connected) { this.currentRequestId = globalThis.crypto.randomUUID(); this.startedAt = this.now(); this.send({type:'audio.commit',requestId:this.currentRequestId}) }
  }
  async disconnect() {
    this.cancelLocal(); await this.stopRecording(false)
    const socket = this.socket; this.socket = null; this.connected = false; this.connecting = null; socket?.close()
    const context = this.playbackContext; this.playbackContext = null
    if (context && context.state !== 'closed') await context.close()
    this.onConnectionChange?.(false)
  }
  async newConversation() { await this.disconnect(); this.conversationId = globalThis.crypto.randomUUID(); this.turnId = null; this.cancelled.clear() }
  feedback(category, turnId = this.turnId) { if (turnId) this.send({type:'feedback',category,turnId}) }
  async toggleRecording(appId) { if (this.recording) await this.stopRecording(); else { await this.connect(appId); await this.startRecording() } return this.recording }
  getConnectionStatus() { return this.connected }
  getRecordingStatus() { return this.recording }
  getPlaybackStatus() { return this.playing }
  getStats() { return {conversationId:this.conversationId,turnId:this.turnId,connected:this.connected,recording:this.recording,playing:this.playing} }
  isActive() { return this.connected || this.recording || this.playing }
}
export function resamplePCM16(input, sampleRate) {
  const ratio = sampleRate / 16000; const pcm = new Int16Array(Math.floor(input.length / ratio))
  for (let i = 0; i < pcm.length; i++) {
    const start = Math.floor(i * ratio); const end = Math.min(input.length, Math.max(start + 1, Math.floor((i + 1) * ratio)))
    let sum = 0; for (let j = start; j < end; j++) sum += input[j]
    const sample = Math.max(-1, Math.min(1, sum / (end - start))); pcm[i] = sample < 0 ? sample * 32768 : sample * 32767
  }
  return pcm
}

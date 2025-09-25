import { ElMessageBox, ElMessage } from 'element-plus'
import { errorManager } from '../services/ErrorManager'
import { ErrorType, ErrorSeverity } from '../types/error'

/**
 * 麦克风权限管理工具
 * 提供权限检查、请求和用户指导功能
 */
export class MicrophonePermissionManager {
  private static instance: MicrophonePermissionManager
  private permissionStatus: PermissionStatus | null = null

  private constructor() {}

  static getInstance(): MicrophonePermissionManager {
    if (!MicrophonePermissionManager.instance) {
      MicrophonePermissionManager.instance = new MicrophonePermissionManager()
    }
    return MicrophonePermissionManager.instance
  }

  /**
   * 检查麦克风权限状态
   */
  async checkPermission(): Promise<PermissionState> {
    try {
      if ('permissions' in navigator) {
        const permission = await navigator.permissions.query({ name: 'microphone' as PermissionName })
        this.permissionStatus = permission
        return permission.state
      }
      
      // 如果不支持权限API，尝试直接访问麦克风
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
        stream.getTracks().forEach(track => track.stop())
        return 'granted'
      } catch (error) {
        return 'denied'
      }
    } catch (error) {
      console.error('Failed to check microphone permission:', error)
      return 'prompt'
    }
  }

  /**
   * 请求麦克风权限
   */
  async requestPermission(): Promise<MediaStream | null> {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
          sampleRate: 16000,
          channelCount: 1
        }
      })

      ElMessage({
        message: '麦克风权限已获取',
        type: 'success',
        duration: 2000
      })

      return stream
    } catch (error) {
      await this.handlePermissionError(error as DOMException)
      return null
    }
  }

  /**
   * 处理权限错误
   */
  private async handlePermissionError(error: DOMException): Promise<void> {
    let errorType: ErrorType
    let message: string
    let guidance: string[]

    switch (error.name) {
      case 'NotAllowedError':
        errorType = ErrorType.PERMISSION_DENIED
        message = '麦克风权限被拒绝'
        guidance = this.getPermissionDeniedGuidance()
        break

      case 'NotFoundError':
        errorType = ErrorType.MICROPHONE_ERROR
        message = '未找到麦克风设备'
        guidance = [
          '请检查麦克风设备是否正确连接',
          '确认设备驱动程序已安装',
          '尝试重新插拔麦克风设备'
        ]
        break

      case 'NotReadableError':
        errorType = ErrorType.MICROPHONE_ERROR
        message = '麦克风设备被其他应用占用'
        guidance = [
          '关闭其他可能使用麦克风的应用',
          '重启浏览器后重试',
          '检查系统音频设置'
        ]
        break

      case 'OverconstrainedError':
        errorType = ErrorType.AUDIO_CONTEXT_ERROR
        message = '麦克风不支持所需的音频格式'
        guidance = [
          '您的麦克风可能不支持所需的音频格式',
          '尝试使用其他麦克风设备',
          '联系技术支持获取帮助'
        ]
        break

      default:
        errorType = ErrorType.MICROPHONE_ERROR
        message = `麦克风访问失败: ${error.message}`
        guidance = [
          '检查麦克风设备连接',
          '确认浏览器支持音频功能',
          '尝试刷新页面重试'
        ]
    }

    const appError = errorManager.createError(errorType, message, ErrorSeverity.HIGH, {
      originalError: error.name,
      guidance
    })

    await errorManager.handleError(appError)
  }

  /**
   * 显示权限指导对话框
   */
  async showPermissionGuidance(): Promise<boolean> {
    try {
      await ElMessageBox.confirm(
        this.getPermissionGuidanceHTML(),
        '需要麦克风权限',
        {
          confirmButtonText: '我已设置，重试',
          cancelButtonText: '取消',
          type: 'warning',
          dangerouslyUseHTMLString: true,
          customClass: 'microphone-permission-dialog'
        }
      )
      return true
    } catch (error) {
      return false
    }
  }

  /**
   * 获取权限被拒绝的指导信息
   */
  private getPermissionDeniedGuidance(): string[] {
    const userAgent = navigator.userAgent.toLowerCase()
    
    if (userAgent.includes('chrome')) {
      return [
        '点击地址栏左侧的锁形图标或摄像头图标',
        '在弹出菜单中选择"麦克风"',
        '选择"允许"或"始终允许"',
        '刷新页面重试'
      ]
    } else if (userAgent.includes('firefox')) {
      return [
        '点击地址栏左侧的盾牌图标',
        '找到"使用麦克风"选项',
        '选择"允许"',
        '刷新页面重试'
      ]
    } else if (userAgent.includes('safari')) {
      return [
        '在Safari菜单中选择"偏好设置"',
        '点击"网站"标签',
        '在左侧选择"麦克风"',
        '找到当前网站并设置为"允许"'
      ]
    } else if (userAgent.includes('edge')) {
      return [
        '点击地址栏右侧的锁形图标',
        '找到"麦克风"权限设置',
        '选择"允许"',
        '刷新页面重试'
      ]
    }

    return [
      '在浏览器设置中找到网站权限',
      '允许当前网站使用麦克风',
      '刷新页面重试'
    ]
  }

  /**
   * 获取权限指导HTML内容
   */
  private getPermissionGuidanceHTML(): string {
    const guidance = this.getPermissionDeniedGuidance()
    const steps = guidance.map((step, index) => 
      `<p><strong>步骤 ${index + 1}:</strong> ${step}</p>`
    ).join('')

    return `
      <div style="text-align: left; line-height: 1.6;">
        <p style="margin-bottom: 16px; color: #606266;">
          为了使用语音功能，需要您的麦克风权限。请按照以下步骤设置：
        </p>
        ${steps}
        <div style="margin-top: 16px; padding: 12px; background: #f0f9ff; border-radius: 4px; border-left: 4px solid #409eff;">
          <p style="margin: 0; font-size: 13px; color: #409eff;">
            <strong>提示：</strong>设置完成后，请点击"我已设置，重试"按钮重新获取权限。
          </p>
        </div>
      </div>
    `
  }

  /**
   * 监听权限状态变化
   */
  onPermissionChange(callback: (state: PermissionState) => void): void {
    if (this.permissionStatus) {
      this.permissionStatus.addEventListener('change', () => {
        callback(this.permissionStatus!.state)
      })
    }
  }

  /**
   * 检查浏览器是否支持麦克风
   */
  isSupported(): boolean {
    return !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia)
  }

  /**
   * 获取可用的音频输入设备
   */
  async getAudioInputDevices(): Promise<MediaDeviceInfo[]> {
    try {
      const devices = await navigator.mediaDevices.enumerateDevices()
      return devices.filter(device => device.kind === 'audioinput')
    } catch (error) {
      console.error('Failed to enumerate audio devices:', error)
      return []
    }
  }

  /**
   * 测试麦克风功能
   */
  async testMicrophone(): Promise<boolean> {
    try {
      const stream = await this.requestPermission()
      if (!stream) return false

      // 创建音频上下文进行简单测试
      const audioContext = new AudioContext()
      const source = audioContext.createMediaStreamSource(stream)
      const analyser = audioContext.createAnalyser()
      
      source.connect(analyser)
      
      // 检测音频输入
      const dataArray = new Uint8Array(analyser.frequencyBinCount)
      analyser.getByteFrequencyData(dataArray)
      
      // 清理资源
      stream.getTracks().forEach(track => track.stop())
      audioContext.close()
      
      ElMessage({
        message: '麦克风测试成功',
        type: 'success',
        duration: 2000
      })
      
      return true
    } catch (error) {
      console.error('Microphone test failed:', error)
      
      ElMessage({
        message: '麦克风测试失败',
        type: 'error',
        duration: 3000
      })
      
      return false
    }
  }
}

// 导出单例实例
export const microphonePermission = MicrophonePermissionManager.getInstance()

// 导出便捷函数
export const checkMicrophonePermission = () => microphonePermission.checkPermission()
export const requestMicrophonePermission = () => microphonePermission.requestPermission()
export const showMicrophoneGuidance = () => microphonePermission.showPermissionGuidance()
export const testMicrophone = () => microphonePermission.testMicrophone()
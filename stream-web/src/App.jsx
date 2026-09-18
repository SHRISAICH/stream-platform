import { Client } from '@stomp/stompjs'
import Hls from 'hls.js'
import { useEffect, useRef, useState } from 'react'
import './App.css'
import AdminDashboardView from './components/AdminDashboardView'
import AnalyticsView from './components/AnalyticsView'
import ScheduleStreamModal from './components/ScheduleStreamModal'

const getHost = () => {
  if (typeof window !== 'undefined' && window.location.hostname) {
    return window.location.hostname
  }
  return 'localhost'
}

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ||
  (typeof window !== 'undefined' &&
  (window.location.port === '5173' || window.location.port === '3000')
    ? `http://${getHost()}:8081`
    : '')

const SRS_HTTP_URL = import.meta.env.VITE_SRS_HTTP_URL || ''

const RTMP_SERVER_BASE =
  import.meta.env.VITE_RTMP_URL || `rtmp://${getHost()}:1935/live`

const getWsUrl = () => {
  if (import.meta.env.VITE_WS_URL) {
    return import.meta.env.VITE_WS_URL
  }
  if (API_BASE_URL.startsWith('https://')) {
    return API_BASE_URL.replace(/^https:/, 'wss:') + '/ws'
  }
  if (API_BASE_URL.startsWith('http://')) {
    return API_BASE_URL.replace(/^http:/, 'ws:') + '/ws'
  }
  if (typeof window !== 'undefined') {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${protocol}//${window.location.host}/ws`
  }
  return 'ws://localhost:8081/ws'
}

const formatChatTime = (dateStr) => {
  if (!dateStr) return ''
  try {
    const d = new Date(dateStr)
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  } catch {
    return ''
  }
}

const formatNotificationTime = (dateStr) => {
  if (!dateStr) return ''
  try {
    const d = new Date(dateStr)
    const now = new Date()
    const diffMs = now - d
    const diffSec = Math.floor(diffMs / 1000)
    if (diffSec < 60) return 'Just now'
    const diffMin = Math.floor(diffSec / 60)
    if (diffMin < 60) return `${diffMin}m ago`
    const diffHours = Math.floor(diffMin / 60)
    if (diffHours < 24) return `${diffHours}h ago`
    const diffDays = Math.floor(diffHours / 24)
    if (diffDays < 7) return `${diffDays}d ago`
    return d.toLocaleDateString([], { month: 'short', day: 'numeric' })
  } catch {
    return ''
  }
}

const CATEGORIES = [
  'All',
  'Technology',
  'Gaming',
  'Creative',
  'Music',
  'Education',
  'Entertainment',
  'Sports',
]

function App() {
  const [token, setToken] = useState(() => localStorage.getItem('token') || '')

  const [currentUser, setCurrentUser] = useState(null)
  const [showProfileModal, setShowProfileModal] = useState(false)

  // Notifications State
  const [notifications, setNotifications] = useState([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [showNotifications, setShowNotifications] = useState(false)
  const [notificationsLoading, setNotificationsLoading] = useState(false)
  const [notificationsError, setNotificationsError] = useState('')
  const notificationDropdownRef = useRef(null)
  const notificationBellRef = useRef(null)

  // Navigation tabs: 'dashboard' | 'explore' | 'my-streams'
  const [currentTab, setCurrentTab] = useState('explore')
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [mobileNavOpen, setMobileNavOpen] = useState(false)

  // Auth Modal State
  const [authMode, setAuthMode] = useState('login')
  const [usernameOrEmail, setUsernameOrEmail] = useState('')
  const [fullName, setFullName] = useState('')
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [authLoading, setAuthLoading] = useState(false)
  const [authError, setAuthError] = useState('')
  const [authSuccess, setAuthSuccess] = useState('')
  const [showLogin, setShowLogin] = useState(false)

  // Streams State
  const [streams, setStreams] = useState([])
  const [streamLoading, setStreamLoading] = useState(false)
  const [streamError, setStreamError] = useState('')

  // Filter & Search
  const [search, setSearch] = useState('')
  const [selectedCategory, setSelectedCategory] = useState('All')

  // Watch / Player Modal
  const [selectedStream, setSelectedStream] = useState(null)
  const [showPlayer, setShowPlayer] = useState(false)

  // Live Chat State
  const [chatMessages, setChatMessages] = useState([])
  const [chatInput, setChatInput] = useState('')
  const [chatLoading, setChatLoading] = useState(false)
  const [chatConnected, setChatConnected] = useState(false)
  const [chatSending, setChatSending] = useState(false)
  const [chatError, setChatError] = useState('')
  const chatMessagesEndRef = useRef(null)
  const chatScrollRef = useRef(null)

  // Create Stream Modal
  const [showCreateStream, setShowCreateStream] = useState(false)
  const [createStreamLoading, setCreateStreamLoading] = useState(false)
  const [createStreamError, setCreateStreamError] = useState('')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [streamCategory, setStreamCategory] = useState('Technology')
  const [isPublic, setIsPublic] = useState(true)

  // Schedule Stream Modal
  const [showScheduleModal, setShowScheduleModal] = useState(false)

  // Edit Stream Modal
  const [showEditStream, setShowEditStream] = useState(false)
  const [editStreamId, setEditStreamId] = useState(null)
  const [editTitle, setEditTitle] = useState('')
  const [editDescription, setEditDescription] = useState('')
  const [editCategory, setEditCategory] = useState('')
  const [editIsPublic, setEditIsPublic] = useState(true)
  const [editStreamLoading, setEditStreamLoading] = useState(false)
  const [editStreamError, setEditStreamError] = useState('')

  // Delete Stream Modal
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [streamToDelete, setStreamToDelete] = useState(null)
  const [deleteStreamLoading, setDeleteStreamLoading] = useState(false)
  const [deleteStreamError, setDeleteStreamError] = useState('')

  // Video / VOD State
  const [videos, setVideos] = useState([])
  const [myVideos, setMyVideos] = useState([])
  const [videoLoading, setVideoLoading] = useState(false)
  const [selectedVideo, setSelectedVideo] = useState(null)
  const [showVideoPlayer, setShowVideoPlayer] = useState(false)

  // Upload Video Modal State
  const [showUploadVideo, setShowUploadVideo] = useState(false)
  const [uploadVideoLoading, setUploadVideoLoading] = useState(false)
  const [uploadVideoProgress, setUploadVideoProgress] = useState(0)
  const [uploadVideoError, setUploadVideoError] = useState('')
  const [uploadFile, setUploadFile] = useState(null)
  const [uploadTitle, setUploadTitle] = useState('')
  const [uploadDescription, setUploadDescription] = useState('')
  const [uploadCategory, setUploadCategory] = useState('Technology')
  const [uploadIsPublic, setUploadIsPublic] = useState(true)

  // Edit Video Modal State
  const [showEditVideo, setShowEditVideo] = useState(false)
  const [editVideoId, setEditVideoId] = useState(null)
  const [editVideoTitle, setEditVideoTitle] = useState('')
  const [editVideoDescription, setEditVideoDescription] = useState('')
  const [editVideoCategory, setEditVideoCategory] = useState('Technology')
  const [editVideoIsPublic, setEditVideoIsPublic] = useState(true)
  const [editVideoLoading, setEditVideoLoading] = useState(false)
  const [editVideoError, setEditVideoError] = useState('')

  // Delete Video Modal State
  const [showDeleteVideoConfirm, setShowDeleteVideoConfirm] = useState(false)
  const [videoToDelete, setVideoToDelete] = useState(null)
  const [deleteVideoLoading, setDeleteVideoLoading] = useState(false)
  const [deleteVideoError, setDeleteVideoError] = useState('')

  // Quick Encoder Guide Modal
  const [showEncoderModal, setShowEncoderModal] = useState(false)

  // Floating Toast Feedback
  const [toastMessage, setToastMessage] = useState('')
  const [showStreamKey, setShowStreamKey] = useState(false)

  const videoRef = useRef(null)
  const hlsRef = useRef(null)

  // Show Toast
  const showToast = (msg) => {
    setToastMessage(msg)
    setTimeout(() => {
      setToastMessage('')
    }, 4000)
  }

  // Copy to Clipboard Helper
  const copyToClipboard = async (text, label = 'Copied to clipboard!') => {
    if (!text) return
    try {
      await navigator.clipboard.writeText(text)
      showToast(label)
    } catch {
      showToast('Copied!')
    }
  }

  // Load Streams
  useEffect(() => {
    let isMounted = true

    const loadStreams = async (showLoading = false) => {
      if (showLoading) {
        setStreamLoading(true)
      }

      try {
        const endpoint = token
          ? `${API_BASE_URL}/api/streams`
          : `${API_BASE_URL}/api/streams/public`

        const headers = { Accept: 'application/json' }
        if (token) {
          headers.Authorization = `Bearer ${token}`
        }

        const response = await fetch(endpoint, { method: 'GET', headers })

        if (response.status === 401 && token) {
          localStorage.removeItem('token')
          if (isMounted) {
            setToken('')
            setCurrentUser(null)
            setStreams([])
            setSelectedStream(null)
            setShowPlayer(false)
            setShowProfileModal(false)
          }
          return
        }

        const responseText = await response.text()

        if (!response.ok) {
          throw new Error(`Streams API failed: ${response.status} ${responseText}`)
        }

        const data = JSON.parse(responseText)
        if (!Array.isArray(data)) {
          throw new Error('Streams API returned unexpected data format.')
        }

        if (isMounted) {
          setStreams(data)

          // Update current selectedStream with real-time viewer count & status
          setSelectedStream((prev) => {
            if (!prev) return prev
            const found = data.find((s) => s.id === prev.id)
            return found || prev
          })

          setStreamError('')
        }
      } catch (err) {
        if (showLoading && isMounted) {
          setStreamError(err.message)
        }
      } finally {
        if (showLoading && isMounted) {
          setStreamLoading(false)
        }
      }
    }

    loadStreams(true)
    const intervalId = setInterval(() => loadStreams(false), 5000)

    return () => {
      isMounted = false
      clearInterval(intervalId)
    }
  }, [token])

  // Load Current User Profile
  useEffect(() => {
    if (!token) return

    let isMounted = true

    const loadProfile = async () => {
      try {
        const response = await fetch(`${API_BASE_URL}/api/users/me`, {
          method: 'GET',
          headers: {
            Authorization: `Bearer ${token}`,
            Accept: 'application/json',
          },
        })

        if (response.status === 401) {
          localStorage.removeItem('token')
          if (isMounted) {
            setToken('')
            setCurrentUser(null)
          }
          return
        }

        if (response.ok) {
          const data = await response.json()
          if (isMounted) {
            setCurrentUser(data)
          }
        }
      } catch {
        // Ignored
      }
    }

    loadProfile()

    return () => {
      isMounted = false
    }
  }, [token])

  // Load Notifications & Unread Count
  useEffect(() => {
    if (!token) return

    let isMounted = true
    const fetchNotifs = async () => {
      setNotificationsLoading(true)
      setNotificationsError('')
      try {
        const [notifResp, countResp] = await Promise.all([
          fetch(`${API_BASE_URL}/api/notifications`, {
            headers: {
              Accept: 'application/json',
              Authorization: `Bearer ${token}`,
            },
          }),
          fetch(`${API_BASE_URL}/api/notifications/unread-count`, {
            headers: {
              Accept: 'application/json',
              Authorization: `Bearer ${token}`,
            },
          }),
        ])

        if (!isMounted) return

        if (notifResp.ok) {
          const notifData = await notifResp.json()
          setNotifications(notifData)
        } else {
          setNotificationsError('Failed to load notifications')
        }

        if (countResp.ok) {
          const countData = await countResp.json()
          setUnreadCount(countData.count ?? countData.unreadCount ?? 0)
        }
      } catch {
        if (isMounted) setNotificationsError('Network error loading notifications')
      } finally {
        if (isMounted) setNotificationsLoading(false)
      }
    }

    fetchNotifs()

    return () => {
      isMounted = false
    }
  }, [token])

  // Real-time Notifications STOMP subscription
  useEffect(() => {
    if (!token || !currentUser?.id) return
    let isMounted = true

    const stompClient = new Client({
      brokerURL: getWsUrl(),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        if (!isMounted) return
        stompClient.subscribe(`/topic/users/${currentUser.id}/notifications`, (msgFrame) => {
          if (!isMounted) return
          try {
            const notif = JSON.parse(msgFrame.body)
            if (notif && notif.id) {
              setNotifications((prev) => {
                if (prev.some((n) => n.id === notif.id)) return prev
                return [notif, ...prev]
              })
              setUnreadCount((prev) => prev + 1)
            }
          } catch (err) {
            console.error('Failed to parse incoming notification', err)
          }
        })
      },
      onStompError: (frame) => {
        console.warn('Notification STOMP broker error:', frame)
      },
    })

    try {
      stompClient.activate()
    } catch (err) {
      console.warn('STOMP activation error for notifications:', err)
    }

    return () => {
      isMounted = false
      try {
        stompClient.deactivate()
      } catch {
        // ignore
      }
    }
  }, [token, currentUser?.id])

  // Close Notification Dropdown on outside click
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (
        showNotifications &&
        notificationDropdownRef.current &&
        !notificationDropdownRef.current.contains(event.target) &&
        notificationBellRef.current &&
        !notificationBellRef.current.contains(event.target)
      ) {
        setShowNotifications(false)
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [showNotifications])

  // Notification action handlers
  const reloadNotifications = async () => {
    if (!token) return
    setNotificationsLoading(true)
    setNotificationsError('')
    try {
      const [notifResp, countResp] = await Promise.all([
        fetch(`${API_BASE_URL}/api/notifications`, {
          headers: {
            Accept: 'application/json',
            Authorization: `Bearer ${token}`,
          },
        }),
        fetch(`${API_BASE_URL}/api/notifications/unread-count`, {
          headers: {
            Accept: 'application/json',
            Authorization: `Bearer ${token}`,
          },
        }),
      ])

      if (notifResp.ok) {
        const notifData = await notifResp.json()
        setNotifications(notifData)
      } else {
        setNotificationsError('Failed to load notifications')
      }

      if (countResp.ok) {
        const countData = await countResp.json()
        setUnreadCount(countData.count ?? countData.unreadCount ?? 0)
      }
    } catch {
      setNotificationsError('Network error loading notifications')
    } finally {
      setNotificationsLoading(false)
    }
  }

  const markNotificationRead = async (id, e) => {
    if (e) e.stopPropagation()
    if (!token) return
    try {
      const resp = await fetch(`${API_BASE_URL}/api/notifications/${id}/read`, {
        method: 'PUT',
        headers: {
          Accept: 'application/json',
          Authorization: `Bearer ${token}`,
        },
      })
      if (resp.ok) {
        setNotifications((prev) =>
          prev.map((n) => (n.id === id ? { ...n, read: true } : n))
        )
        setUnreadCount((prev) => Math.max(0, prev - 1))
      }
    } catch {
      // ignore
    }
  }

  const markAllNotificationsRead = async () => {
    if (!token) return
    try {
      const resp = await fetch(`${API_BASE_URL}/api/notifications/read-all`, {
        method: 'PUT',
        headers: {
          Accept: 'application/json',
          Authorization: `Bearer ${token}`,
        },
      })
      if (resp.ok) {
        setNotifications((prev) => prev.map((n) => ({ ...n, read: true })))
        setUnreadCount(0)
      }
    } catch {
      // ignore
    }
  }

  const deleteNotificationItem = async (id, wasRead, e) => {
    if (e) e.stopPropagation()
    if (!token) return
    try {
      const resp = await fetch(`${API_BASE_URL}/api/notifications/${id}`, {
        method: 'DELETE',
        headers: {
          Accept: 'application/json',
          Authorization: `Bearer ${token}`,
        },
      })
      if (resp.ok) {
        setNotifications((prev) => prev.filter((n) => n.id !== id))
        if (!wasRead) {
          setUnreadCount((prev) => Math.max(0, prev - 1))
        }
      }
    } catch {
      // ignore
    }
  }

  const handleNotificationClick = (notif) => {
    if (!notif.read) {
      markNotificationRead(notif.id)
    }
    if (notif.relatedStreamId) {
      const st = streams.find((s) => s.id === notif.relatedStreamId)
      if (st) {
        setSelectedStream(st)
        setShowPlayer(true)
        setShowNotifications(false)
      } else {
        fetch(`${API_BASE_URL}/api/streams/${notif.relatedStreamId}`, {
          headers: {
            Accept: 'application/json',
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
        })
          .then((res) => (res.ok ? res.json() : null))
          .then((data) => {
            if (data) {
              setSelectedStream(data)
              setShowPlayer(true)
              setShowNotifications(false)
            }
          })
          .catch(() => {})
      }
    }
  }

  // Load Videos (Public Catalog & Creator Videos)
  useEffect(() => {
    let isMounted = true

    const loadVideos = async (showLoading = false) => {
      if (showLoading && isMounted) setVideoLoading(true)

      try {
        const pubResp = await fetch(`${API_BASE_URL}/api/videos/public`, {
          headers: { Accept: 'application/json' },
        })
        if (pubResp.ok && isMounted) {
          const pubData = await pubResp.json()
          setVideos(pubData)
        }

        if (token) {
          const myResp = await fetch(`${API_BASE_URL}/api/videos`, {
            headers: {
              Accept: 'application/json',
              Authorization: `Bearer ${token}`,
            },
          })
          if (myResp.ok && isMounted) {
            const myData = await myResp.json()
            setMyVideos(myData)
          }
        } else if (isMounted) {
          setMyVideos([])
        }
      } catch {
        // Ignored
      } finally {
        if (showLoading && isMounted) setVideoLoading(false)
      }
    }

    loadVideos(true)
    const intervalId = setInterval(() => loadVideos(false), 8000)

    return () => {
      isMounted = false
      clearInterval(intervalId)
    }
  }, [token])

  // HLS Video Lifecycle
  const rawPlaybackUrl =
    selectedStream?.playbackUrl ||
    (selectedStream?.streamKey
      ? `${SRS_HTTP_URL}/live/${selectedStream.streamKey}.m3u8`
      : '')

  const playbackSourceUrl = rawPlaybackUrl
    ? (rawPlaybackUrl.startsWith('/')
        ? rawPlaybackUrl
        : rawPlaybackUrl)
    : ''

  useEffect(() => {
    if (!showPlayer || !playbackSourceUrl || !videoRef.current) return

    const video = videoRef.current
    if (hlsRef.current) {
      hlsRef.current.destroy()
      hlsRef.current = null
    }

    let hls
    if (Hls.isSupported()) {
      hls = new Hls({
        lowLatencyMode: true,
        backBufferLength: 30,
      })
      hlsRef.current = hls
      hls.loadSource(playbackSourceUrl)
      hls.attachMedia(video)

      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        video.play().catch(() => {})
      })

      hls.on(Hls.Events.ERROR, (_evt, data) => {
        if (data.fatal) {
          switch (data.type) {
            case Hls.ErrorTypes.NETWORK_ERROR:
              hls.startLoad()
              break
            case Hls.ErrorTypes.MEDIA_ERROR:
              hls.recoverMediaError()
              break
            default:
              hls.destroy()
              break
          }
        }
      })
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = playbackSourceUrl
      video.play().catch(() => {})
    }

    return () => {
      if (hls) hls.destroy()
      hlsRef.current = null
      video.pause()
      video.removeAttribute('src')
      video.load()
    }
  }, [showPlayer, playbackSourceUrl])

  // Live Chat WebSocket & Fetch Lifecycle
  useEffect(() => {
    if (!showPlayer || !selectedStream) {
      return
    }

    let isMounted = true
    const streamId = selectedStream.id

    // 1. Fetch recent messages
    fetch(`${API_BASE_URL}/api/streams/${streamId}/chat`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
      .then((res) => {
        if (!res.ok) throw new Error('Failed to load chat history')
        return res.json()
      })
      .then((data) => {
        if (isMounted && Array.isArray(data)) {
          setChatMessages(data)
        }
      })
      .catch((err) => {
        if (isMounted) console.warn('Could not load chat messages:', err.message)
      })
      .finally(() => {
        if (isMounted) setChatLoading(false)
      })

    // 2. Connect STOMP WebSocket
    const stompClient = new Client({
      brokerURL: getWsUrl(),
      reconnectDelay: 3000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        if (!isMounted) return
        setChatConnected(true)
        stompClient.subscribe(`/topic/streams/${streamId}/chat`, (msgFrame) => {
          if (!isMounted) return
          try {
            const event = JSON.parse(msgFrame.body)
            if (event.type === 'MESSAGE' && event.message) {
              setChatMessages((prev) => {
                if (prev.some((m) => m.id === event.message.id)) return prev
                return [...prev, event.message]
              })
            } else if (event.type === 'DELETE' && event.messageId) {
              setChatMessages((prev) => prev.filter((m) => m.id !== event.messageId))
            }
          } catch (err) {
            console.error('Failed to parse chat event', err)
          }
        })
      },
      onDisconnect: () => {
        if (isMounted) setChatConnected(false)
      },
      onStompError: () => {
        if (isMounted) setChatConnected(false)
      },
      onWebSocketClose: () => {
        if (isMounted) setChatConnected(false)
      },
    })

    try {
      stompClient.activate()
    } catch (err) {
      console.warn('STOMP activation error:', err)
    }

    return () => {
      isMounted = false
      try {
        stompClient.deactivate()
      } catch {
        // ignore
      }
      setChatConnected(false)
      setChatMessages([])
    }
  }, [showPlayer, selectedStream, token])

  // Auto-scroll chat to bottom on new messages
  useEffect(() => {
    if (chatMessagesEndRef.current && showPlayer) {
      chatMessagesEndRef.current.scrollIntoView({ behavior: 'smooth' })
    }
  }, [chatMessages, showPlayer])

  // Send Chat Message Handler
  const handleSendChatMessage = async (e) => {
    e.preventDefault()
    const content = chatInput.trim()
    if (!content || !selectedStream || !token || chatSending) return

    setChatSending(true)
    setChatError('')

    try {
      const res = await fetch(`${API_BASE_URL}/api/streams/${selectedStream.id}/chat`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ content }),
      })

      if (!res.ok) {
        const errorData = await res.json().catch(() => null)
        throw new Error(errorData?.message || 'Failed to send message')
      }

      const newMsg = await res.json()
      setChatInput('')
      setChatMessages((prev) => {
        if (prev.some((m) => m.id === newMsg.id)) return prev
        return [...prev, newMsg]
      })
    } catch (err) {
      setChatError(err.message || 'Failed to send message')
    } finally {
      setChatSending(false)
    }
  }

  // Delete Chat Message Handler
  const handleDeleteChatMessage = async (messageId) => {
    if (!messageId || !token) return
    try {
      const res = await fetch(`${API_BASE_URL}/api/chat/${messageId}`, {
        method: 'DELETE',
        headers: {
          Authorization: `Bearer ${token}`,
        },
      })
      if (res.ok) {
        setChatMessages((prev) => prev.filter((m) => m.id !== messageId))
      } else {
        const errorData = await res.json().catch(() => null)
        showToast(errorData?.message || 'Failed to delete message')
      }
    } catch {
      showToast('Error deleting message')
    }
  }

  // Auth Handler
  const handleAuth = async (e) => {
    e.preventDefault()
    setAuthLoading(true)
    setAuthError('')
    setAuthSuccess('')

    const endpoint =
      authMode === 'login'
        ? `${API_BASE_URL}/api/auth/login`
        : `${API_BASE_URL}/api/auth/register`

    const body =
      authMode === 'login'
        ? { usernameOrEmail, password }
        : { fullName, username, email, password }

    try {
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
        },
        body: JSON.stringify(body),
      })

      const responseText = await response.text()

      if (!response.ok) {
        let msg = `Authentication error (${response.status})`
        try {
          const errObj = JSON.parse(responseText)
          if (errObj && errObj.message) msg = errObj.message
        } catch {
          if (responseText) msg = responseText
        }
        throw new Error(msg)
      }

      if (authMode === 'login') {
        const data = JSON.parse(responseText)
        if (!data.token) throw new Error('No token returned.')
        localStorage.setItem('token', data.token)
        setToken(data.token)
        setPassword('')
        setShowLogin(false)
        showToast('Welcome back to StrataLive!')
      } else {
        setAuthSuccess('Registration successful! You can now sign in.')
        setAuthMode('login')
        setPassword('')
      }
    } catch (err) {
      setAuthError(err.message)
    } finally {
      setAuthLoading(false)
    }
  }

  // Logout Handler
  const handleLogout = () => {
    localStorage.removeItem('token')
    setToken('')
    setCurrentUser(null)
    setShowProfileModal(false)
    setShowPlayer(false)
    setSelectedStream(null)
    setNotifications([])
    setUnreadCount(0)
    setShowNotifications(false)
    showToast('Signed out successfully')
  }

  // Create Stream Handler
  const handleCreateStream = async (e) => {
    e.preventDefault()
    setCreateStreamLoading(true)
    setCreateStreamError('')

    try {
      const response = await fetch(`${API_BASE_URL}/api/streams`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          title,
          description,
          category: streamCategory,
          public: isPublic,
        }),
      })

      const responseText = await response.text()
      if (!response.ok) {
        throw new Error(`Failed to create stream: ${response.status} ${responseText}`)
      }

      const newStream = JSON.parse(responseText)
      setStreams((prev) => [newStream, ...prev])
      setSelectedStream(newStream)

      setTitle('')
      setDescription('')
      setStreamCategory('Technology')
      setIsPublic(true)
      setShowCreateStream(false)
      showToast('Live stream created successfully!')

      // Automatically open player or credentials view
      setShowPlayer(true)
      setShowStreamKey(true)
    } catch (err) {
      setCreateStreamError(err.message)
    } finally {
      setCreateStreamLoading(false)
    }
  }

  // Edit Stream Handler
  const openEditModal = (stream) => {
    setEditStreamId(stream.id)
    setEditTitle(stream.title || '')
    setEditDescription(stream.description || '')
    setEditCategory(stream.category || 'Technology')
    setEditIsPublic(stream.public !== false)
    setEditStreamError('')
    setShowEditStream(true)
  }

  const handleUpdateStream = async (e) => {
    e.preventDefault()
    if (!editStreamId) return

    setEditStreamLoading(true)
    setEditStreamError('')

    try {
      const response = await fetch(`${API_BASE_URL}/api/streams/${editStreamId}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          title: editTitle,
          description: editDescription,
          category: editCategory,
          public: editIsPublic,
        }),
      })

      if (response.status === 401) {
        handleLogout()
        return
      }

      const responseText = await response.text()
      if (!response.ok) {
        throw new Error(`Update failed (${response.status})`)
      }

      const updated = JSON.parse(responseText)
      setStreams((prev) => prev.map((s) => (s.id === updated.id ? updated : s)))
      setSelectedStream((prev) => (prev && prev.id === updated.id ? updated : prev))
      setShowEditStream(false)
      showToast('Stream settings updated.')
    } catch (err) {
      setEditStreamError(err.message)
    } finally {
      setEditStreamLoading(false)
    }
  }

  // Delete Stream Handler
  const openDeleteModal = (stream) => {
    setStreamToDelete(stream)
    setDeleteStreamError('')
    setShowDeleteConfirm(true)
  }

  const handleDeleteStream = async () => {
    if (!streamToDelete) return
    setDeleteStreamLoading(true)
    setDeleteStreamError('')

    try {
      const response = await fetch(`${API_BASE_URL}/api/streams/${streamToDelete.id}`, {
        method: 'DELETE',
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'application/json',
        },
      })

      if (response.status === 401) {
        handleLogout()
        return
      }

      if (!response.ok) {
        throw new Error(`Delete failed (${response.status})`)
      }

      const deletedId = streamToDelete.id
      setStreams((prev) => prev.filter((s) => s.id !== deletedId))
      if (selectedStream && selectedStream.id === deletedId) {
        setSelectedStream(null)
        setShowPlayer(false)
      }
      setShowDeleteConfirm(false)
      setStreamToDelete(null)
      showToast('Stream channel deleted.')
    } catch (err) {
      setDeleteStreamError(err.message)
    } finally {
      setDeleteStreamLoading(false)
    }
  }

  // Cancel Scheduled Stream Handler
  const handleCancelScheduledStream = async (streamId, e) => {
    if (e) e.stopPropagation()
    if (!window.confirm('Are you sure you want to cancel this scheduled stream?')) {
      return
    }
    try {
      const response = await fetch(`${API_BASE_URL}/api/streams/${streamId}/cancel`, {
        method: 'PUT',
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'application/json',
        },
      })
      if (!response.ok) {
        throw new Error(`Cancel failed (${response.status})`)
      }
      setStreams((prev) =>
        prev.map((s) => (s.id === streamId ? { ...s, status: 'CANCELLED' } : s))
      )
      showToast('Scheduled stream cancelled.')
    } catch (err) {
      showToast(err.message || 'Error cancelling stream.')
    }
  }

  // Open Stream in Watch View
  const openWatchView = (stream) => {
    setSelectedStream(stream)
    setShowPlayer(true)
    setShowVideoPlayer(false)
    setSelectedVideo(null)
    setShowStreamKey(false)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  // Open Video in Watch View
  const openWatchVideo = (video) => {
    setSelectedVideo(video)
    setShowVideoPlayer(true)
    setShowPlayer(false)
    setSelectedStream(null)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  // Upload Video Handler (multipart/form-data via XMLHttpRequest for progress)
  const handleUploadVideo = (e) => {
    e.preventDefault()
    if (!uploadFile) {
      setUploadVideoError('Please select a video file.')
      return
    }

    setUploadVideoLoading(true)
    setUploadVideoProgress(0)
    setUploadVideoError('')

    const formData = new FormData()
    formData.append('file', uploadFile)
    formData.append('title', uploadTitle.trim() || uploadFile.name)
    formData.append('description', uploadDescription.trim())
    formData.append('category', uploadCategory)
    formData.append('public', uploadIsPublic)

    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${API_BASE_URL}/api/videos`)
    if (token) {
      xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    }
    xhr.setRequestHeader('Accept', 'application/json')

    xhr.upload.onprogress = (evt) => {
      if (evt.lengthComputable) {
        const pct = Math.round((evt.loaded / evt.total) * 100)
        setUploadVideoProgress(pct)
      }
    }

    xhr.onload = () => {
      setUploadVideoLoading(false)
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          const newVideo = JSON.parse(xhr.responseText)
          setMyVideos((prev) => [newVideo, ...prev])
          if (newVideo.public) {
            setVideos((prev) => [newVideo, ...prev])
          }
        } catch {
          // Ignored
        }

        setShowUploadVideo(false)
        setUploadFile(null)
        setUploadTitle('')
        setUploadDescription('')
        setUploadCategory('Technology')
        setUploadIsPublic(true)
        setUploadVideoProgress(0)
        showToast('Video uploaded successfully!')
      } else {
        let msg = `Upload failed (${xhr.status})`
        try {
          const errObj = JSON.parse(xhr.responseText)
          if (errObj && errObj.message) msg = errObj.message
        } catch {
          if (xhr.responseText) msg = xhr.responseText
        }
        setUploadVideoError(msg)
      }
    }

    xhr.onerror = () => {
      setUploadVideoLoading(false)
      setUploadVideoError('Network error occurred during video upload.')
    }

    xhr.send(formData)
  }

  // Edit Video Modal Handler
  const openEditVideoModal = (video) => {
    setEditVideoId(video.id)
    setEditVideoTitle(video.title)
    setEditVideoDescription(video.description || '')
    setEditVideoCategory(video.category || 'Technology')
    setEditVideoIsPublic(video.public)
    setEditVideoError('')
    setShowEditVideo(true)
  }

  const handleUpdateVideo = async (e) => {
    e.preventDefault()
    if (!editVideoId) return
    setEditVideoLoading(true)
    setEditVideoError('')

    try {
      const resp = await fetch(`${API_BASE_URL}/api/videos/${editVideoId}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          title: editVideoTitle,
          description: editVideoDescription,
          category: editVideoCategory,
          public: editVideoIsPublic,
        }),
      })

      if (resp.status === 401) {
        handleLogout()
        return
      }

      if (!resp.ok) {
        const txt = await resp.text()
        throw new Error(`Update failed (${resp.status}): ${txt}`)
      }

      const updated = await resp.json()
      setVideos((prev) => prev.map((v) => (v.id === updated.id ? updated : v)))
      setMyVideos((prev) => prev.map((v) => (v.id === updated.id ? updated : v)))
      if (selectedVideo && selectedVideo.id === updated.id) {
        setSelectedVideo(updated)
      }
      setShowEditVideo(false)
      showToast('Video details updated.')
    } catch (err) {
      setEditVideoError(err.message)
    } finally {
      setEditVideoLoading(false)
    }
  }

  // Delete Video Modal Handler
  const openDeleteVideoModal = (video) => {
    setVideoToDelete(video)
    setDeleteVideoError('')
    setShowDeleteVideoConfirm(true)
  }

  const confirmDeleteVideo = async () => {
    if (!videoToDelete) return
    setDeleteVideoLoading(true)
    setDeleteVideoError('')

    try {
      const resp = await fetch(`${API_BASE_URL}/api/videos/${videoToDelete.id}`, {
        method: 'DELETE',
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'application/json',
        },
      })

      if (resp.status === 401) {
        handleLogout()
        return
      }

      if (!resp.ok && resp.status !== 204) {
        throw new Error(`Delete failed (${resp.status})`)
      }

      const deletedId = videoToDelete.id
      setVideos((prev) => prev.filter((v) => v.id !== deletedId))
      setMyVideos((prev) => prev.filter((v) => v.id !== deletedId))
      if (selectedVideo && selectedVideo.id === deletedId) {
        setShowVideoPlayer(false)
        setSelectedVideo(null)
      }
      setShowDeleteVideoConfirm(false)
      setVideoToDelete(null)
      showToast('Video deleted successfully.')
    } catch (err) {
      setDeleteVideoError(err.message)
    } finally {
      setDeleteVideoLoading(false)
    }
  }

  // Filtered Streams
  const filteredStreams = streams.filter((stream) => {
    const matchesSearch =
      !search ||
      (stream.title && stream.title.toLowerCase().includes(search.toLowerCase())) ||
      (stream.description && stream.description.toLowerCase().includes(search.toLowerCase())) ||
      (stream.category && stream.category.toLowerCase().includes(search.toLowerCase()))

    const matchesCategory =
      selectedCategory === 'All' ||
      (selectedCategory === 'Live' && stream.status === 'LIVE') ||
      stream.category === selectedCategory

    return matchesSearch && matchesCategory
  })

  // Filtered Videos
  const filteredVideos = videos.filter((video) => {
    const matchesSearch =
      !search ||
      (video.title && video.title.toLowerCase().includes(search.toLowerCase())) ||
      (video.description && video.description.toLowerCase().includes(search.toLowerCase())) ||
      (video.category && video.category.toLowerCase().includes(search.toLowerCase())) ||
      (video.creatorUsername && video.creatorUsername.toLowerCase().includes(search.toLowerCase()))

    const matchesCategory =
      selectedCategory === 'All' ||
      selectedCategory === 'Live' ||
      video.category === selectedCategory

    return matchesSearch && matchesCategory
  })

  const videoPlaybackSrc = selectedVideo?.playbackUrl
    ? (selectedVideo.playbackUrl.startsWith('http')
        ? selectedVideo.playbackUrl
        : `${API_BASE_URL}${selectedVideo.playbackUrl.startsWith('/') ? '' : '/'}${selectedVideo.playbackUrl}`)
    : ''

  // Live Streams specifically
  const liveStreams = streams.filter((s) => s.status === 'LIVE')
  const totalLiveViewers = liveStreams.reduce((acc, s) => acc + (s.viewerCount || 0), 0)

  // Full RTMP Publish URL calculation
  const fullRtmpPublishUrl = selectedStream?.streamKey
    ? `${RTMP_SERVER_BASE}/${selectedStream.streamKey}`
    : ''

  const ffmpegCommand = selectedStream?.streamKey
    ? `ffmpeg -re -i input.mp4 -c:v libx264 -preset veryfast -c:a aac -f flv "${RTMP_SERVER_BASE}/${selectedStream.streamKey}"`
    : ''

  const userInitial = (
    currentUser?.fullName?.[0] ||
    currentUser?.username?.[0] ||
    'U'
  ).toUpperCase()

  const isAdmin = Boolean(currentUser && (currentUser.role === 'ADMIN' || currentUser.role === 'ROLE_ADMIN'))

  return (
    <div className="strata-app">
      {/* ====================================================================
          1. TOPBAR NAVIGATION
          ==================================================================== */}
      <header className="topbar">
        <div className="topbar-left">
          <button
            className="menu-toggle-btn"
            onClick={() => {
              setSidebarCollapsed(!sidebarCollapsed)
              setMobileNavOpen(!mobileNavOpen)
            }}
            aria-label="Toggle Navigation"
          >
            ☰
          </button>

          <div
            className="brand-wrapper"
            onClick={() => {
              setCurrentTab('explore')
              setShowPlayer(false)
            }}
          >
            <div className="brand-icon">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <polygon points="5 3 19 12 5 21 5 3"></polygon>
              </svg>
            </div>
            <div className="brand-text">
              <span className="brand-name">StrataLive</span>
              <span className="brand-badge">REAL-TIME MEDIA</span>
            </div>
          </div>
        </div>

        {/* Center Search Input */}
        <div className="search-container">
          <div className="search-input-wrapper">
            <span className="search-icon-inside">⌕</span>
            <input
              type="text"
              className="search-input"
              placeholder="Search streams by title, topic, or category..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            {search && (
              <button
                className="search-clear-btn"
                onClick={() => setSearch('')}
                aria-label="Clear Search"
              >
                ✕
              </button>
            )}
          </div>
        </div>

        {/* Topbar Right Actions */}
        <div className="topbar-right">
          {liveStreams.length > 0 && (
            <div className="live-channels-pill">
              <span className="live-pulse-dot" />
              <span>{liveStreams.length} Channels Live</span>
            </div>
          )}

          <button
            className="btn-primary-gradient"
            style={{ background: 'linear-gradient(135deg, #06b6d4 0%, #3b82f6 100%)' }}
            onClick={() => {
              if (token) {
                setShowUploadVideo(true)
              } else {
                setAuthMode('login')
                setAuthError('')
                setShowLogin(true)
              }
            }}
          >
            <span>⬆</span>
            <span>Upload Video</span>
          </button>

          <button
            className="btn-primary-gradient"
            onClick={() => {
              if (token) {
                setShowCreateStream(true)
              } else {
                setAuthMode('login')
                setShowLogin(true)
              }
            }}
          >
            <span>＋</span>
            <span>Go Live</span>
          </button>

          {/* Notification Bell (when authenticated) */}
          {token && (
            <div className="notification-bell-wrapper">
              <button
                ref={notificationBellRef}
                className={`notification-bell-btn ${showNotifications ? 'active' : ''}`}
                onClick={() => {
                  setShowNotifications((prev) => {
                    const next = !prev
                    if (next) {
                      reloadNotifications()
                    }
                    return next
                  })
                }}
                title="Notifications"
                aria-label="Notifications"
              >
                <svg
                  className="notification-bell-icon"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
                  <path d="M13.73 21a2 2 0 0 1-3.46 0" />
                </svg>
                {unreadCount > 0 && (
                  <span className="notification-badge">
                    {unreadCount > 99 ? '99+' : unreadCount}
                  </span>
                )}
              </button>

              {/* Notification Dropdown Panel */}
              {showNotifications && (
                <div
                  ref={notificationDropdownRef}
                  className="notification-dropdown"
                >
                  <div className="notification-dropdown-header">
                    <div className="notification-header-title">
                      <span>Notifications</span>
                      {unreadCount > 0 && (
                        <span className="notification-header-count">
                          {unreadCount} new
                        </span>
                      )}
                    </div>
                    {unreadCount > 0 && (
                      <button
                        className="notification-mark-all-btn"
                        onClick={markAllNotificationsRead}
                      >
                        ✓ Mark all read
                      </button>
                    )}
                  </div>

                  <div className="notification-dropdown-body">
                    {notificationsLoading && notifications.length === 0 && (
                      <div className="notification-loading-state">
                        <div className="notification-skeleton-row" />
                        <div className="notification-skeleton-row" />
                        <div className="notification-skeleton-row" />
                      </div>
                    )}

                    {notificationsError && (
                      <div className="notification-error-state">
                        <span>{notificationsError}</span>
                        <button
                          className="notification-retry-btn"
                          onClick={reloadNotifications}
                        >
                          Retry
                        </button>
                      </div>
                    )}

                    {!notificationsLoading &&
                      !notificationsError &&
                      notifications.length === 0 && (
                        <div className="notification-empty-state">
                          <div className="notification-empty-icon">🔔</div>
                          <p className="notification-empty-title">All caught up!</p>
                          <p className="notification-empty-desc">
                            No notifications right now.
                          </p>
                        </div>
                      )}

                    {notifications.length > 0 && (
                      <div className="notification-list">
                        {notifications.map((item) => (
                          <div
                            key={item.id}
                            className={`notification-item ${
                              !item.read ? 'unread' : 'read'
                            } ${item.relatedStreamId ? 'clickable' : ''}`}
                            onClick={() => handleNotificationClick(item)}
                          >
                            <div className="notification-item-icon-col">
                              {item.type === 'STREAM_LIVE' ? (
                                <span className="notification-type-pill live">
                                  🔴 Live
                                </span>
                              ) : item.type === 'STREAM_ENDED' ? (
                                <span className="notification-type-pill ended">
                                  ⏹ Ended
                                </span>
                              ) : item.type === 'STREAM_CREATED' ? (
                                <span className="notification-type-pill stream">
                                  🎥 Stream
                                </span>
                              ) : item.type === 'VIDEO_READY' ? (
                                <span className="notification-type-pill vod">
                                  🎬 Video
                                </span>
                              ) : (
                                <span className="notification-type-pill info">
                                  📢 Alert
                                </span>
                              )}
                            </div>
                            <div className="notification-item-content">
                              <div className="notification-item-top">
                                <h4 className="notification-item-title">
                                  {item.title}
                                </h4>
                                <span className="notification-item-time">
                                  {formatNotificationTime(item.createdAt)}
                                </span>
                              </div>
                              <p className="notification-item-msg">
                                {item.message}
                              </p>
                              {item.relatedStreamId && (
                                <span className="notification-watch-hint">
                                  ▶ Click to watch stream
                                </span>
                              )}
                            </div>
                            <div className="notification-item-actions">
                              {!item.read && (
                                <button
                                  className="notif-action-btn notif-read-btn"
                                  onClick={(e) => markNotificationRead(item.id, e)}
                                  title="Mark as read"
                                  aria-label="Mark as read"
                                >
                                  ✓
                                </button>
                              )}
                              <button
                                className="notif-action-btn notif-delete-btn"
                                onClick={(e) =>
                                  deleteNotificationItem(item.id, item.read, e)
                                }
                                title="Delete"
                                aria-label="Delete notification"
                              >
                                ✕
                              </button>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          )}

          {token ? (
            <div
              className="user-nav-capsule"
              onClick={() => setShowProfileModal(true)}
              title="Creator Profile"
            >
              <div className="user-avatar-circle">{userInitial}</div>
              <span className="user-nav-label">
                {currentUser?.fullName || currentUser?.username || 'Creator'}
              </span>
            </div>
          ) : (
            <div style={{ display: 'flex', gap: '8px' }}>
              <button
                className="btn-ghost"
                onClick={() => {
                  setAuthMode('login')
                  setAuthError('')
                  setShowLogin(true)
                }}
              >
                Sign In
              </button>
            </div>
          )}
        </div>
      </header>

      {/* ====================================================================
          2. BODY (SIDEBAR + MAIN CONTENT)
          ==================================================================== */}
      <div className="app-body">
        <aside className={`sidebar ${sidebarCollapsed ? 'collapsed' : ''} ${mobileNavOpen ? 'mobile-open' : ''}`}>
          <div className="nav-section">
            <span className="nav-section-title">Navigation</span>
            <button
              className={`nav-tab-btn ${currentTab === 'dashboard' ? 'active' : ''}`}
              onClick={() => {
                setCurrentTab('dashboard')
                setShowPlayer(false)
                setShowVideoPlayer(false)
                setMobileNavOpen(false)
              }}
            >
              <span className="nav-icon">⚡</span>
              <span className="nav-label">Studio Dashboard</span>
            </button>

            <button
              className={`nav-tab-btn ${currentTab === 'explore' ? 'active' : ''}`}
              onClick={() => {
                setCurrentTab('explore')
                setShowPlayer(false)
                setShowVideoPlayer(false)
                setMobileNavOpen(false)
              }}
            >
              <span className="nav-icon">🧭</span>
              <span className="nav-label">Explore Streams</span>
            </button>

            <button
              className={`nav-tab-btn ${currentTab === 'videos' ? 'active' : ''}`}
              onClick={() => {
                setCurrentTab('videos')
                setShowPlayer(false)
                setShowVideoPlayer(false)
                setMobileNavOpen(false)
              }}
            >
              <span className="nav-icon">🎬</span>
              <span className="nav-label">Explore Videos</span>
            </button>

            <button
              className={`nav-tab-btn ${currentTab === 'my-streams' ? 'active' : ''}`}
              onClick={() => {
                if (!token) {
                  setShowLogin(true)
                  return
                }
                setCurrentTab('my-streams')
                setShowPlayer(false)
                setShowVideoPlayer(false)
                setMobileNavOpen(false)
              }}
            >
              <span className="nav-icon">📹</span>
              <span className="nav-label">My Broadcasts</span>
            </button>

            <button
              className={`nav-tab-btn ${currentTab === 'my-videos' ? 'active' : ''}`}
              onClick={() => {
                if (!token) {
                  setShowLogin(true)
                  return
                }
                setCurrentTab('my-videos')
                setShowPlayer(false)
                setShowVideoPlayer(false)
                setMobileNavOpen(false)
              }}
            >
              <span className="nav-icon">📁</span>
              <span className="nav-label">My Videos</span>
            </button>

            <button
              className={`nav-tab-btn ${currentTab === 'analytics' ? 'active' : ''}`}
              onClick={() => {
                if (!token) {
                  setShowLogin(true)
                  return
                }
                setCurrentTab('analytics')
                setShowPlayer(false)
                setShowVideoPlayer(false)
                setMobileNavOpen(false)
              }}
            >
              <span className="nav-icon">📊</span>
              <span className="nav-label">Analytics</span>
            </button>

            {isAdmin && (
              <button
                className={`nav-tab-btn ${currentTab === 'admin' ? 'active' : ''}`}
                style={{ color: currentTab === 'admin' ? '#fca5a5' : '#f87171' }}
                onClick={() => {
                  setCurrentTab('admin')
                  setShowPlayer(false)
                  setShowVideoPlayer(false)
                  setMobileNavOpen(false)
                }}
              >
                <span className="nav-icon">🛡️</span>
                <span className="nav-label">Admin Console</span>
              </button>
            )}
          </div>

          {/* Category Filters */}
          <div className="nav-section" style={{ marginTop: '16px' }}>
            <span className="nav-section-title">Categories</span>
            <div className="category-filter-list">
              {CATEGORIES.map((cat) => (
                <button
                  key={cat}
                  className={`category-nav-btn ${selectedCategory === cat ? 'active' : ''}`}
                  onClick={() => {
                    setSelectedCategory(cat)
                    setCurrentTab('explore')
                    setShowPlayer(false)
                    setMobileNavOpen(false)
                  }}
                >
                  <span className="cat-dot" />
                  <span>{cat}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Sidebar Footer */}
          <div className="sidebar-footer">
            {token ? (
              <div
                className="sidebar-user-card"
                onClick={() => setShowProfileModal(true)}
                style={{ cursor: 'pointer' }}
              >
                <div className="user-avatar-circle" style={{ width: '28px', height: '28px', fontSize: '11px' }}>
                  {userInitial}
                </div>
                <div className="user-meta">
                  <div style={{ fontSize: '13px', fontWeight: '600', color: 'var(--text-primary)' }}>
                    {currentUser?.username || 'Creator'}
                  </div>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                    {currentUser?.role || 'Creator'}
                  </div>
                </div>
              </div>
            ) : (
              <button
                className="btn-ghost"
                style={{ width: '100%', justifyContent: 'center' }}
                onClick={() => {
                  setAuthMode('login')
                  setShowLogin(true)
                }}
              >
                Sign In
              </button>
            )}
          </div>
        </aside>

        {/* ====================================================================
            3. MAIN VIEWPORT
            ==================================================================== */}
        <main className="main-viewport">
          {/* Error Banner */}
          {streamError && (
            <div className="error-banner">
              <span>⚠️ {streamError}</span>
              <button
                className="btn-mini"
                onClick={() => window.location.reload()}
              >
                Retry
              </button>
            </div>
          )}

          {/* ====================================================================
              VIEW A-2: DEDICATED CINEMATIC VIDEO (VOD) PLAYER
              ==================================================================== */}
          {showVideoPlayer && selectedVideo ? (
            <div className="watch-container">
              <button
                className="back-to-browse-btn"
                onClick={() => setShowVideoPlayer(false)}
              >
                ← Back to Videos
              </button>

              {/* Native HTML5 Video Player */}
              <div className="cinema-player-shell">
                <div className="player-overlay-top">
                  <div className="badge-vod" style={{ position: 'static' }}>
                    <span>🎬</span>
                    <span>VOD RECORDING</span>
                  </div>

                  <div className="player-viewer-pill">
                    <span>💾</span>
                    <span>{(selectedVideo.fileSize / (1024 * 1024)).toFixed(1)} MB</span>
                  </div>
                </div>

                <div className="video-player-box">
                  <video
                    className="video-native-element"
                    src={videoPlaybackSrc}
                    controls
                    autoPlay
                    playsInline
                  >
                    Your browser does not support the video tag.
                  </video>
                </div>
              </div>

              {/* Video Information & Details Card */}
              <div className="stream-detail-meta-card">
                <div className="stream-meta-header-row">
                  <div>
                    <h1 className="stream-view-title">{selectedVideo.title}</h1>
                    <div className="stream-view-submeta">
                      <span className="category-tag">{selectedVideo.category}</span>
                      <span className="video-creator-pill">
                        <span>👤</span>
                        <span>@{selectedVideo.creatorUsername || selectedVideo.creatorFullName || 'Creator'}</span>
                      </span>
                      <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                        Uploaded: {new Date(selectedVideo.createdAt).toLocaleDateString()}
                      </span>
                      <span className="badge-visibility">{selectedVideo.public ? 'Public' : 'Private'}</span>
                    </div>
                  </div>

                  {token && (selectedVideo.userId === currentUser?.id || selectedVideo.creatorUsername === currentUser?.username) && (
                    <div className="stream-action-group">
                      <button
                        className="btn-ghost"
                        onClick={() => openEditVideoModal(selectedVideo)}
                      >
                        Edit Details
                      </button>
                      <button
                        className="btn-ghost"
                        style={{ color: '#f87171', borderColor: 'rgba(239, 68, 68, 0.3)' }}
                        onClick={() => openDeleteVideoModal(selectedVideo)}
                      >
                        Delete
                      </button>
                    </div>
                  )}
                </div>

                {selectedVideo.description && (
                  <div className="stream-description-box">
                    {selectedVideo.description}
                  </div>
                )}
              </div>
            </div>
          ) : showPlayer && selectedStream ? (
            <div className="watch-container">
              <button
                className="back-to-browse-btn"
                onClick={() => setShowPlayer(false)}
              >
                ← Back to Streams
              </button>

              <div className="watch-stage-layout">
                {/* Left Primary Column: Video & Stream Details */}
                <div className="watch-main-stage">
                  {/* Video Player Frame */}
                  <div className="cinema-player-shell">
                    <div className="player-overlay-top">
                      {selectedStream.status === 'LIVE' ? (
                        <div className="player-status-badge">
                          <span className="live-dot-mini" />
                          <span>LIVE NOW</span>
                        </div>
                      ) : (
                        <div className="badge-offline">
                          <span>OFFLINE</span>
                        </div>
                      )}

                      <div className="player-viewer-pill">
                        <span>👁️</span>
                        <span>{selectedStream.viewerCount || 0} watching</span>
                      </div>
                    </div>

                    {selectedStream.status === 'LIVE' ? (
                      <video
                        ref={videoRef}
                        className="live-video-element"
                        controls
                        autoPlay
                        playsInline
                      />
                    ) : (
                      <div className="player-offline-canvas">
                        <div className="offline-icon-circle">📡</div>
                        <h2>Broadcast is Currently Offline</h2>
                        <p>
                          The broadcaster has not started pushing an active RTMP feed to this channel.
                          When streaming begins, video will appear automatically.
                        </p>
                      </div>
                    )}
                  </div>

                  {/* Below Player Stream Information */}
                  <div className="stream-info-panel">
                    <div className="stream-info-header">
                      <div>
                        <h1 className="stream-main-title">{selectedStream.title}</h1>
                        <div className="stream-tags-row">
                          <span className={`status-pill ${selectedStream.status === 'LIVE' ? 'live' : 'offline'}`}>
                            {selectedStream.status}
                          </span>
                          <span className="category-tag">{selectedStream.category}</span>
                          <span className="viewer-count-pill">
                            <span>●</span>
                            <span>{selectedStream.viewerCount || 0} Viewers</span>
                          </span>
                          <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                            Created: {new Date(selectedStream.createdAt).toLocaleDateString()}
                          </span>
                        </div>
                      </div>

                      {token && selectedStream.streamKey && (
                        <div className="stream-action-group">
                          <button
                            className="btn-ghost"
                            onClick={() => openEditModal(selectedStream)}
                          >
                            Edit Stream
                          </button>
                          <button
                            className="btn-ghost"
                            style={{ color: '#f87171', borderColor: 'rgba(239, 68, 68, 0.3)' }}
                            onClick={() => openDeleteModal(selectedStream)}
                          >
                            Delete
                          </button>
                        </div>
                      )}
                    </div>

                    {selectedStream.description && (
                      <div className="stream-description-box">
                        {selectedStream.description}
                      </div>
                    )}

                    {/* Streamer Private Credentials & Setup (if streamKey exists) */}
                    {selectedStream.streamKey && (
                      <div className="stream-setup-card">
                        <div className="setup-card-header">
                          <div className="setup-card-title">
                            <span>🔑</span>
                            <span>Broadcaster Setup & Secret Stream Key</span>
                          </div>
                          <button
                            className="btn-mini"
                            onClick={() => setShowStreamKey(!showStreamKey)}
                          >
                            {showStreamKey ? 'Hide Secret Key' : 'Reveal Secret Key'}
                          </button>
                        </div>

                        <div className="security-warning-notice">
                          <span>⚠️</span>
                          <span>Never share your secret Stream Key on camera or with viewers. Anyone with this key can publish to your channel.</span>
                        </div>

                        <div className="credential-field">
                          <span className="credential-label">Secret Stream Key</span>
                          <div className="credential-input-bar">
                            <span className="credential-value">
                              {showStreamKey
                                ? selectedStream.streamKey
                                : `${selectedStream.streamKey.slice(0, 4)}••••••••••••••••`}
                            </span>
                            <button
                              className="btn-mini"
                              onClick={() => copyToClipboard(selectedStream.streamKey, 'Stream Key copied!')}
                            >
                              Copy Key
                            </button>
                          </div>
                        </div>

                        <div className="credential-field">
                          <span className="credential-label">RTMP Ingest Server</span>
                          <div className="credential-input-bar">
                            <span className="credential-value">{RTMP_SERVER_BASE}</span>
                            <button
                              className="btn-mini"
                              onClick={() => copyToClipboard(RTMP_SERVER_BASE, 'RTMP Server URL copied!')}
                            >
                              Copy URL
                            </button>
                          </div>
                        </div>

                        <div className="credential-field">
                          <span className="credential-label">Full Publishing Destination</span>
                          <div className="credential-input-bar">
                            <span className="credential-value">
                              {showStreamKey
                                ? fullRtmpPublishUrl
                                : `${RTMP_SERVER_BASE}/${selectedStream.streamKey.slice(0, 4)}••••••••`}
                            </span>
                            <button
                              className="btn-mini"
                              onClick={() => copyToClipboard(fullRtmpPublishUrl, 'Full RTMP URL copied!')}
                            >
                              Copy Full URL
                            </button>
                          </div>
                        </div>

                        <div className="credential-field" style={{ marginBottom: 0 }}>
                          <span className="credential-label">FFmpeg Publish Command</span>
                          <div className="credential-input-bar">
                            <span className="credential-value" style={{ fontSize: '11px' }}>{ffmpegCommand}</span>
                            <button
                              className="btn-mini"
                              onClick={() => copyToClipboard(ffmpegCommand, 'FFmpeg command copied!')}
                            >
                              Copy Command
                            </button>
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                </div>

                {/* Right Sidebar Stage: Live Chat Panel */}
                <div className="watch-sidebar-stage">
                  <div className="live-chat-panel">
                    <div className="chat-panel-header">
                      <div className="chat-panel-title">
                        <span className="chat-title-icon">💬</span>
                        <span>Live Stream Chat</span>
                      </div>
                      <div className="chat-connection-badge">
                        <span className={`chat-conn-dot ${chatConnected ? 'connected' : 'connecting'}`} />
                        <span>{chatConnected ? 'Live' : 'Connecting...'}</span>
                      </div>
                    </div>

                    <div className="chat-messages-container" ref={chatScrollRef}>
                      {chatLoading ? (
                        <div className="chat-empty-state">
                          <span className="chat-empty-icon">⏳</span>
                          <p>Loading chat history...</p>
                        </div>
                      ) : chatMessages.length === 0 ? (
                        <div className="chat-empty-state">
                          <span className="chat-empty-icon">✨</span>
                          <p>Welcome to the stream chat! Say hello to the community.</p>
                        </div>
                      ) : (
                        chatMessages.map((msg) => {
                          const isOwnMessage = currentUser && msg.userId === currentUser.id
                          const isStreamOwner = !!selectedStream.streamKey
                          const canDelete = isOwnMessage || isStreamOwner
                          const timeStr = formatChatTime(msg.createdAt)

                          return (
                            <div
                              key={msg.id}
                              className={`chat-message-bubble ${isOwnMessage ? 'own-message' : ''}`}
                            >
                              <div className="chat-message-header">
                                <span className="chat-author-name">
                                  {msg.userFullName || msg.username}
                                  {isStreamOwner && isOwnMessage && (
                                    <span className="chat-badge-host">HOST</span>
                                  )}
                                </span>
                                <span className="chat-message-time">{timeStr}</span>
                                {token && canDelete && (
                                  <button
                                    type="button"
                                    className="chat-delete-btn"
                                    title="Delete message"
                                    onClick={() => handleDeleteChatMessage(msg.id)}
                                  >
                                    ✕
                                  </button>
                                )}
                              </div>
                              <div className="chat-message-body">{msg.content}</div>
                            </div>
                          )
                        })
                      )}
                      <div ref={chatMessagesEndRef} />
                    </div>

                    {chatError && (
                      <div className="chat-error-banner">
                        <span>⚠️ {chatError}</span>
                      </div>
                    )}

                    <div className="chat-input-bar">
                      {token ? (
                        <form onSubmit={handleSendChatMessage} className="chat-form">
                          <input
                            type="text"
                            className="chat-input"
                            placeholder="Send a message (max 500 chars)..."
                            maxLength={500}
                            value={chatInput}
                            onChange={(e) => setChatInput(e.target.value)}
                            disabled={chatSending}
                          />
                          <button
                            type="submit"
                            className="chat-send-btn"
                            disabled={chatSending || !chatInput.trim()}
                            title="Send Message"
                          >
                            {chatSending ? '...' : 'Send'}
                          </button>
                        </form>
                      ) : (
                        <div className="chat-login-prompt">
                          <span>Sign in to chat with viewers</span>
                          <button
                            type="button"
                            className="btn-chat-login"
                            onClick={() => setShowLogin(true)}
                          >
                            Sign In
                          </button>
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          ) : currentTab === 'dashboard' ? (
            /* ====================================================================
               VIEW B: STUDIO DASHBOARD / COMMAND CENTER
               ==================================================================== */
            <div>
              <div className="dashboard-hero">
                <div className="hero-top-row">
                  <div className="hero-intro">
                    <h1>Creator Command Center</h1>
                    <p>
                      Manage high-throughput RTMP ingestion, monitor real-time audience metrics,
                      and distribute video globally with low-latency HLS.
                    </p>
                  </div>

                  <div className="hero-cta-group">
                    <button
                      className="btn-primary-gradient"
                      onClick={() => {
                        if (token) {
                          setShowCreateStream(true)
                        } else {
                          setShowLogin(true)
                        }
                      }}
                    >
                      <span>＋</span>
                      <span>Create Channel</span>
                    </button>

                    <button
                      className="btn-ghost"
                      onClick={() => {
                        if (token) {
                          setShowScheduleModal(true)
                        } else {
                          setShowLogin(true)
                        }
                      }}
                    >
                      <span>📅</span>
                      <span>Schedule Stream</span>
                    </button>

                    <button
                      className="btn-ghost"
                      onClick={() => setShowEncoderModal(true)}
                    >
                      <span>⚡</span>
                      <span>Encoder Setup</span>
                    </button>
                  </div>
                </div>

                {/* Metric Summary Grid */}
                <div className="metric-grid">
                  <div className="metric-card">
                    <div className="metric-icon-box red">📡</div>
                    <div className="metric-data">
                      <span className="metric-value">{liveStreams.length}</span>
                      <span className="metric-label">Live Channels</span>
                    </div>
                  </div>

                  <div className="metric-card">
                    <div className="metric-icon-box blue">👁️</div>
                    <div className="metric-data">
                      <span className="metric-value">{totalLiveViewers}</span>
                      <span className="metric-label">Total Live Audience</span>
                    </div>
                  </div>

                  <div className="metric-card">
                    <div className="metric-icon-box indigo">📼</div>
                    <div className="metric-data">
                      <span className="metric-value">{streams.length}</span>
                      <span className="metric-label">Registered Streams</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Live Now Highlight Section */}
              {liveStreams.length > 0 && (
                <div style={{ marginBottom: '36px' }}>
                  <div className="section-heading-bar">
                    <div className="section-title-wrap">
                      <span className="live-pulse-dot" />
                      <h2 className="section-title">Broadcasting Live Now</h2>
                    </div>
                  </div>

                  <div className="stream-grid">
                    {liveStreams.map((stream) => (
                      <article
                        key={stream.id}
                        className="stream-card"
                        onClick={() => openWatchView(stream)}
                      >
                        <div className="thumbnail-box">
                          <div className="thumbnail-mesh" />
                          <div className="badge-live">
                            <span className="live-dot-mini" />
                            <span>LIVE</span>
                          </div>
                          <div className="badge-viewers">
                            <span>👁️</span>
                            <span>{stream.viewerCount || 0}</span>
                          </div>
                          <div className="thumbnail-center-icon">▶</div>
                        </div>

                        <div className="card-content">
                          <h3 className="card-title">{stream.title}</h3>
                          {stream.description && (
                            <p className="card-description">{stream.description}</p>
                          )}
                          <div className="card-footer-meta">
                            <span className="category-tag">{stream.category}</span>
                            <span className="card-action-hint">Watch Live →</span>
                          </div>
                        </div>
                      </article>
                    ))}
                  </div>
                </div>
              )}

              {/* All Creator Streams */}
              <div className="section-heading-bar">
                <div className="section-title-wrap">
                  <h2 className="section-title">All Channels & Broadcasts</h2>
                  <span className="section-count-badge">{streams.length}</span>
                </div>
              </div>

              {streamLoading ? (
                <div className="stream-grid">
                  {[1, 2, 3].map((i) => (
                    <div key={i} className="skeleton-card">
                      <div className="skeleton-thumb" />
                      <div className="skeleton-body">
                        <div className="skeleton-line title" />
                        <div className="skeleton-line sub" />
                      </div>
                    </div>
                  ))}
                </div>
              ) : streams.length === 0 ? (
                <div className="empty-state-card">
                  <div className="empty-state-icon">📹</div>
                  <h3>No Stream Channels Registered</h3>
                  <p>Create your first broadcast channel to obtain an RTMP stream key and begin streaming.</p>
                  <button
                    className="btn-primary-gradient"
                    onClick={() => {
                      if (token) setShowCreateStream(true)
                      else setShowLogin(true)
                    }}
                  >
                    Create Channel Now
                  </button>
                </div>
              ) : (
                <div className="stream-grid">
                  {streams.map((stream) => (
                    <article
                      key={stream.id}
                      className="stream-card"
                      onClick={() => openWatchView(stream)}
                    >
                      <div className="thumbnail-box">
                        <div className="thumbnail-mesh" />
                        {stream.status === 'LIVE' ? (
                          <div className="badge-live">
                            <span className="live-dot-mini" />
                            <span>LIVE</span>
                          </div>
                        ) : (
                          <div className="badge-offline">OFFLINE</div>
                        )}
                        <div className="badge-viewers">
                          <span>👁️</span>
                          <span>{stream.viewerCount || 0}</span>
                        </div>
                        <span className="badge-visibility">{stream.public ? 'Public' : 'Private'}</span>
                        <div className="thumbnail-center-icon">▶</div>
                      </div>

                      <div className="card-content">
                        <h3 className="card-title">{stream.title}</h3>
                        {stream.description && (
                          <p className="card-description">{stream.description}</p>
                        )}
                        <div className="card-footer-meta">
                          <span className="category-tag">{stream.category}</span>
                          <span className="card-action-hint">View Stream →</span>
                        </div>
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </div>
          ) : currentTab === 'videos' ? (
            /* ====================================================================
               VIEW E: EXPLORE VIDEOS (VOD DISCOVERY)
               ==================================================================== */
            <div>
              <div className="section-heading-bar">
                <div className="section-title-wrap">
                  <h1 className="section-title">
                    {selectedCategory === 'All' || selectedCategory === 'Live'
                      ? 'Recorded Videos & VOD'
                      : `${selectedCategory} Videos`}
                  </h1>
                  <span className="section-count-badge">{filteredVideos.length}</span>
                </div>

                {token && (
                  <button
                    className="btn-primary-gradient"
                    style={{ background: 'linear-gradient(135deg, #06b6d4 0%, #3b82f6 100%)' }}
                    onClick={() => setShowUploadVideo(true)}
                  >
                    <span>⬆</span>
                    <span>Upload Video</span>
                  </button>
                )}
              </div>

              {videoLoading ? (
                <div className="stream-grid">
                  {[1, 2, 3, 4, 5, 6].map((i) => (
                    <div key={i} className="skeleton-card">
                      <div className="skeleton-thumb" />
                      <div className="skeleton-body">
                        <div className="skeleton-line title" />
                        <div className="skeleton-line sub" />
                      </div>
                    </div>
                  ))}
                </div>
              ) : filteredVideos.length === 0 ? (
                <div className="empty-state-card">
                  <div className="empty-state-icon">🎬</div>
                  <h3>No Videos Found</h3>
                  <p>
                    {search
                      ? `No uploaded videos matching "${search}". Try searching for another topic.`
                      : 'No recorded videos currently available in this category.'}
                  </p>
                  {(search || (selectedCategory !== 'All' && selectedCategory !== 'Live')) && (
                    <button
                      className="btn-ghost"
                      onClick={() => {
                        setSearch('')
                        setSelectedCategory('All')
                      }}
                      style={{ marginTop: '12px' }}
                    >
                      Reset Filters
                    </button>
                  )}
                  {token && (
                    <button
                      className="btn-primary-gradient"
                      style={{ marginTop: '12px', marginLeft: '8px' }}
                      onClick={() => setShowUploadVideo(true)}
                    >
                      Upload the First Video
                    </button>
                  )}
                </div>
              ) : (
                <div className="stream-grid">
                  {filteredVideos.map((video) => (
                    <article
                      key={video.id}
                      className="stream-card"
                      onClick={() => openWatchVideo(video)}
                    >
                      <div className="thumbnail-box">
                        <div className="thumbnail-mesh" />
                        <div className="badge-vod">VOD</div>
                        <div className="badge-viewers">
                          <span>💾</span>
                          <span>{(video.fileSize / (1024 * 1024)).toFixed(1)}M</span>
                        </div>
                        <span className="badge-visibility">{video.public ? 'Public' : 'Private'}</span>
                        <div className="thumbnail-center-icon">▶</div>
                      </div>

                      <div className="card-content">
                        <h3 className="card-title">{video.title}</h3>
                        {video.description && (
                          <p className="card-description">{video.description}</p>
                        )}
                        <div className="card-footer-meta">
                          <span className="category-tag">{video.category}</span>
                          <span className="card-action-hint">Watch Video →</span>
                        </div>
                        <div style={{ marginTop: '6px', fontSize: '11px', color: 'var(--text-muted)' }}>
                          By @{video.creatorUsername || video.creatorFullName || 'Creator'} • {new Date(video.createdAt).toLocaleDateString()}
                        </div>
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </div>
          ) : currentTab === 'my-streams' ? (
            /* ====================================================================
               VIEW C: MY STREAMS (CREATOR MANAGEMENT)
               ==================================================================== */
            <div>
              <div className="section-heading-bar">
                <div>
                  <h1 className="section-title">My Broadcasting Channels</h1>
                  <p style={{ color: 'var(--text-muted)', fontSize: '14px', marginTop: '4px' }}>
                    Manage and configure your personal live streaming keys and channel details.
                  </p>
                </div>

                <div style={{ display: 'flex', gap: '10px' }}>
                  <button
                    className="btn-ghost"
                    onClick={() => setShowScheduleModal(true)}
                  >
                    <span>📅</span>
                    <span>Schedule Stream</span>
                  </button>
                  <button
                    className="btn-primary-gradient"
                    onClick={() => setShowCreateStream(true)}
                  >
                    <span>＋</span>
                    <span>New Stream</span>
                  </button>
                </div>
              </div>

              {streams.length === 0 ? (
                <div className="empty-state-card">
                  <div className="empty-state-icon">📡</div>
                  <h3>No Channels Yet</h3>
                  <p>You haven't created any live stream channels yet. Create one now to get your RTMP stream key.</p>
                  <div style={{ display: 'flex', gap: '12px', justifyContent: 'center', marginTop: '12px' }}>
                    <button
                      className="btn-primary-gradient"
                      onClick={() => setShowCreateStream(true)}
                    >
                      Create Channel
                    </button>
                    <button
                      className="btn-ghost"
                      onClick={() => setShowScheduleModal(true)}
                    >
                      Schedule Stream
                    </button>
                  </div>
                </div>
              ) : (
                <div className="stream-grid">
                  {streams.map((stream) => (
                    <article key={stream.id} className="stream-card" onClick={() => openWatchView(stream)}>
                      <div className="thumbnail-box">
                        <div className="thumbnail-mesh" />
                        {stream.status === 'LIVE' ? (
                          <div className="badge-live">
                            <span className="live-dot-mini" />
                            <span>LIVE</span>
                          </div>
                        ) : stream.status === 'SCHEDULED' ? (
                          <div className="badge-scheduled">
                            <span>📅</span>
                            <span>SCHEDULED</span>
                          </div>
                        ) : stream.status === 'ENDED' ? (
                          <div className="badge-ended">ENDED</div>
                        ) : stream.status === 'CANCELLED' ? (
                          <div className="badge-cancelled">CANCELLED</div>
                        ) : (
                          <div className="badge-offline">OFFLINE</div>
                        )}
                        <div className="badge-viewers">
                          <span>👁️</span>
                          <span>{stream.viewerCount || 0}</span>
                        </div>
                        <span className="badge-visibility">{stream.public ? 'Public' : 'Private'}</span>
                        <div className="thumbnail-center-icon">▶</div>
                      </div>

                      <div className="card-content">
                        <h3 className="card-title">{stream.title}</h3>
                        {stream.description && (
                          <p className="card-description">{stream.description}</p>
                        )}
                        {stream.scheduledStartTime && (
                          <div style={{ fontSize: '12px', color: '#a5b4fc', marginTop: '6px', display: 'flex', alignItems: 'center', gap: '5px' }}>
                            <span>🕒</span>
                            <span>Scheduled: {new Date(stream.scheduledStartTime).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })}</span>
                          </div>
                        )}
                        <div className="card-footer-meta" style={{ marginTop: '10px' }}>
                          <span className="category-tag">{stream.category}</span>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                            {stream.status === 'SCHEDULED' && (
                              <button
                                className="btn-ghost"
                                style={{ padding: '2px 8px', fontSize: '11px', color: '#f87171' }}
                                onClick={(e) => handleCancelScheduledStream(stream.id, e)}
                              >
                                Cancel
                              </button>
                            )}
                            <span className="card-action-hint">Manage Channel →</span>
                          </div>
                        </div>
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </div>
          ) : currentTab === 'my-videos' ? (
            /* ====================================================================
               VIEW F: MY VIDEOS (CREATOR VOD STUDIO)
               ==================================================================== */
            <div>
              <div className="section-heading-bar">
                <div>
                  <h1 className="section-title">My Uploaded Videos</h1>
                  <p style={{ color: 'var(--text-muted)', fontSize: '14px', marginTop: '4px' }}>
                    Manage and publish your recorded video uploads stored securely in MinIO.
                  </p>
                </div>

                <button
                  className="btn-primary-gradient"
                  style={{ background: 'linear-gradient(135deg, #06b6d4 0%, #3b82f6 100%)' }}
                  onClick={() => setShowUploadVideo(true)}
                >
                  <span>⬆</span>
                  <span>Upload Video</span>
                </button>
              </div>

              {myVideos.length === 0 ? (
                <div className="empty-state-card">
                  <div className="empty-state-icon">📁</div>
                  <h3>No Videos Uploaded Yet</h3>
                  <p>You haven't uploaded any recorded videos to MinIO yet. Upload high-definition MP4 or WebM videos to start your VOD library.</p>
                  <button
                    className="btn-primary-gradient"
                    onClick={() => setShowUploadVideo(true)}
                    style={{ marginTop: '12px' }}
                  >
                    Upload Your First Video
                  </button>
                </div>
              ) : (
                <div className="stream-grid">
                  {myVideos.map((video) => (
                    <article
                      key={video.id}
                      className="stream-card"
                      onClick={() => openWatchVideo(video)}
                    >
                      <div className="thumbnail-box">
                        <div className="thumbnail-mesh" />
                        <div className="badge-vod">VOD</div>
                        <div className="badge-viewers">
                          <span>💾</span>
                          <span>{(video.fileSize / (1024 * 1024)).toFixed(1)}M</span>
                        </div>
                        <span className="badge-visibility">{video.public ? 'Public' : 'Private'}</span>
                        <div className="thumbnail-center-icon">▶</div>
                      </div>

                      <div className="card-content">
                        <h3 className="card-title">{video.title}</h3>
                        {video.description && (
                          <p className="card-description">{video.description}</p>
                        )}
                        <div className="card-footer-meta">
                          <span className="category-tag">{video.category}</span>
                          <span className="card-action-hint">Manage Video →</span>
                        </div>
                        <div style={{ marginTop: '6px', fontSize: '11px', color: 'var(--text-muted)' }}>
                          Uploaded {new Date(video.createdAt).toLocaleDateString()}
                        </div>
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </div>
          ) : currentTab === 'analytics' ? (
            /* ====================================================================
               VIEW G: CREATOR ANALYTICS DASHBOARD
               ==================================================================== */
            <AnalyticsView
              token={token}
              apiBaseUrl={API_BASE_URL}
              onOpenStream={(stream) => openWatchView(stream)}
            />
          ) : currentTab === 'admin' ? (
            /* ====================================================================
               VIEW H: ADMIN GOVERNANCE CONSOLE
               ==================================================================== */
            <AdminDashboardView
              token={token}
              apiBaseUrl={API_BASE_URL}
              currentUser={currentUser}
              showToast={showToast}
            />
          ) : (
            /* ====================================================================
               VIEW D: EXPLORE / PUBLIC STREAMS
               ==================================================================== */
            <div>
              <div className="section-heading-bar">
                <div className="section-title-wrap">
                  <h1 className="section-title">
                    {selectedCategory === 'All'
                      ? 'Explore Live Streams'
                      : `${selectedCategory} Streams`}
                  </h1>
                  <span className="section-count-badge">{filteredStreams.length}</span>
                </div>
              </div>

              {streamLoading ? (
                <div className="stream-grid">
                  {[1, 2, 3, 4, 5, 6].map((i) => (
                    <div key={i} className="skeleton-card">
                      <div className="skeleton-thumb" />
                      <div className="skeleton-body">
                        <div className="skeleton-line title" />
                        <div className="skeleton-line sub" />
                      </div>
                    </div>
                  ))}
                </div>
              ) : filteredStreams.length === 0 ? (
                <div className="empty-state-card">
                  <div className="empty-state-icon">🔍</div>
                  <h3>No Streams Found</h3>
                  <p>
                    {search
                      ? `No broadcasts matching "${search}". Try searching for another topic.`
                      : `No streams currently available in ${selectedCategory}.`}
                  </p>
                  {(search || selectedCategory !== 'All') && (
                    <button
                      className="btn-ghost"
                      onClick={() => {
                        setSearch('')
                        setSelectedCategory('All')
                      }}
                    >
                      Reset Filters
                    </button>
                  )}
                </div>
              ) : (
                <div className="stream-grid">
                  {filteredStreams.map((stream) => (
                    <article
                      key={stream.id}
                      className="stream-card"
                      onClick={() => openWatchView(stream)}
                    >
                      <div className="thumbnail-box">
                        <div className="thumbnail-mesh" />
                        {stream.status === 'LIVE' ? (
                          <div className="badge-live">
                            <span className="live-dot-mini" />
                            <span>LIVE</span>
                          </div>
                        ) : stream.status === 'SCHEDULED' ? (
                          <div className="badge-scheduled">
                            <span>📅</span>
                            <span>SCHEDULED</span>
                          </div>
                        ) : stream.status === 'ENDED' ? (
                          <div className="badge-ended">ENDED</div>
                        ) : stream.status === 'CANCELLED' ? (
                          <div className="badge-cancelled">CANCELLED</div>
                        ) : (
                          <div className="badge-offline">OFFLINE</div>
                        )}
                        <div className="badge-viewers">
                          <span>👁️</span>
                          <span>{stream.viewerCount || 0}</span>
                        </div>
                        <div className="thumbnail-center-icon">▶</div>
                      </div>

                      <div className="card-content">
                        <h3 className="card-title">{stream.title}</h3>
                        {stream.description && (
                          <p className="card-description">{stream.description}</p>
                        )}
                        {stream.scheduledStartTime && (
                          <div style={{ fontSize: '12px', color: '#a5b4fc', marginTop: '6px', display: 'flex', alignItems: 'center', gap: '5px' }}>
                            <span>🕒</span>
                            <span>Scheduled: {new Date(stream.scheduledStartTime).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })}</span>
                          </div>
                        )}
                        <div className="card-footer-meta" style={{ marginTop: '8px' }}>
                          <span className="category-tag">{stream.category}</span>
                          <span className="card-action-hint">
                            {stream.status === 'SCHEDULED' ? 'View Details →' : 'Watch Stream →'}
                          </span>
                        </div>
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </div>
          )}
        </main>
      </div>

      {/* ====================================================================
          4. MODALS & STUDIO OVERLAYS
          ==================================================================== */}

      {/* A. Create Stream Modal */}
      {showCreateStream && (
        <div className="modal-backdrop" onClick={() => setShowCreateStream(false)}>
          <div className="modal-dialog" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="modal-title">Create Live Broadcast Channel</h3>
              <button className="modal-close-btn" onClick={() => setShowCreateStream(false)}>✕</button>
            </div>

            <form onSubmit={handleCreateStream}>
              <div className="modal-body">
                {createStreamError && (
                  <div className="error-banner" style={{ marginBottom: '16px' }}>
                    <span>{createStreamError}</span>
                  </div>
                )}

                <div className="form-group">
                  <label className="form-label">Channel Title *</label>
                  <input
                    type="text"
                    required
                    className="form-input"
                    placeholder="e.g. Distributed Systems & Live Streaming"
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">Category</label>
                  <select
                    className="form-select"
                    value={streamCategory}
                    onChange={(e) => setStreamCategory(e.target.value)}
                  >
                    {CATEGORIES.filter((c) => c !== 'All' && c !== 'Live').map((c) => (
                      <option key={c} value={c}>{c}</option>
                    ))}
                  </select>
                </div>

                <div className="form-group">
                  <label className="form-label">Description</label>
                  <textarea
                    className="form-textarea"
                    placeholder="Describe what your stream is about..."
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                  />
                </div>

                <div className="form-group">
                  <div
                    className="visibility-toggle-card"
                    onClick={() => setIsPublic(!isPublic)}
                  >
                    <div className="visibility-meta">
                      <h4>{isPublic ? 'Public Broadcast' : 'Private Stream'}</h4>
                      <p>
                        {isPublic
                          ? 'Anyone can discover and watch this live stream without logging in.'
                          : 'Only you can view this stream.'}
                      </p>
                    </div>
                    <input
                      type="checkbox"
                      checked={isPublic}
                      onChange={() => {}}
                      style={{ transform: 'scale(1.25)', accentColor: 'var(--primary)' }}
                    />
                  </div>
                </div>
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  className="btn-ghost"
                  onClick={() => setShowCreateStream(false)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn-primary-gradient"
                  disabled={createStreamLoading}
                >
                  {createStreamLoading ? 'Generating Channel...' : 'Create Channel & Get Key'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Schedule Stream Modal */}
      <ScheduleStreamModal
        isOpen={showScheduleModal}
        onClose={() => setShowScheduleModal(false)}
        token={token}
        apiBaseUrl={API_BASE_URL}
        onStreamScheduled={(newStream) => {
          setStreams((prev) => [newStream, ...prev])
          setSelectedStream(newStream)
          setShowPlayer(true)
          setShowStreamKey(true)
        }}
        showToast={showToast}
      />

      {/* B. Edit Stream Modal */}
      {showEditStream && (
        <div className="modal-backdrop" onClick={() => setShowEditStream(false)}>
          <div className="modal-dialog" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="modal-title">Edit Stream Channel</h3>
              <button className="modal-close-btn" onClick={() => setShowEditStream(false)}>✕</button>
            </div>

            <form onSubmit={handleUpdateStream}>
              <div className="modal-body">
                {editStreamError && (
                  <div className="error-banner" style={{ marginBottom: '16px' }}>
                    <span>{editStreamError}</span>
                  </div>
                )}

                <div className="form-group">
                  <label className="form-label">Title *</label>
                  <input
                    type="text"
                    required
                    className="form-input"
                    value={editTitle}
                    onChange={(e) => setEditTitle(e.target.value)}
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">Category</label>
                  <select
                    className="form-select"
                    value={editCategory}
                    onChange={(e) => setEditCategory(e.target.value)}
                  >
                    {CATEGORIES.filter((c) => c !== 'All' && c !== 'Live').map((c) => (
                      <option key={c} value={c}>{c}</option>
                    ))}
                  </select>
                </div>

                <div className="form-group">
                  <label className="form-label">Description</label>
                  <textarea
                    className="form-textarea"
                    value={editDescription}
                    onChange={(e) => setEditDescription(e.target.value)}
                  />
                </div>

                <div className="form-group">
                  <div
                    className="visibility-toggle-card"
                    onClick={() => setEditIsPublic(!editIsPublic)}
                  >
                    <div className="visibility-meta">
                      <h4>{editIsPublic ? 'Public Broadcast' : 'Private Stream'}</h4>
                      <p>
                        {editIsPublic
                          ? 'Public streams are listed on the discovery explore page.'
                          : 'Private streams are hidden from public browse views.'}
                      </p>
                    </div>
                    <input
                      type="checkbox"
                      checked={editIsPublic}
                      onChange={() => {}}
                      style={{ transform: 'scale(1.25)', accentColor: 'var(--primary)' }}
                    />
                  </div>
                </div>
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  className="btn-ghost"
                  onClick={() => setShowEditStream(false)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn-primary-gradient"
                  disabled={editStreamLoading}
                >
                  {editStreamLoading ? 'Saving...' : 'Save Changes'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* C. Delete Stream Confirmation Modal */}
      {showDeleteConfirm && streamToDelete && (
        <div className="modal-backdrop" onClick={() => setShowDeleteConfirm(false)}>
          <div className="modal-dialog" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '440px' }}>
            <div className="modal-header">
              <h3 className="modal-title" style={{ color: '#f87171' }}>Delete Broadcast Channel</h3>
              <button className="modal-close-btn" onClick={() => setShowDeleteConfirm(false)}>✕</button>
            </div>

            <div className="modal-body">
              {deleteStreamError && (
                <div className="error-banner" style={{ marginBottom: '16px' }}>
                  <span>{deleteStreamError}</span>
                </div>
              )}
              <p style={{ color: 'var(--text-secondary)', fontSize: '14px', lineHeight: '1.5' }}>
                Are you sure you want to delete <strong style={{ color: 'white' }}>{streamToDelete.title}</strong>?
                This action is permanent and will immediately revoke its publishing key.
              </p>
            </div>

            <div className="modal-footer">
              <button
                type="button"
                className="btn-ghost"
                onClick={() => setShowDeleteConfirm(false)}
              >
                Keep Channel
              </button>
              <button
                type="button"
                className="btn-primary-gradient"
                style={{ background: 'linear-gradient(135deg, #ef4444 0%, #dc2626 100%)', boxShadow: '0 4px 14px rgba(239, 68, 68, 0.4)' }}
                onClick={handleDeleteStream}
                disabled={deleteStreamLoading}
              >
                {deleteStreamLoading ? 'Deleting...' : 'Yes, Delete'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* D. Authentication Modal (Login / Register) */}
      {showLogin && (
        <div className="modal-backdrop" onClick={() => setShowLogin(false)}>
          <div className="modal-dialog" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '420px' }}>
            <div className="modal-header" style={{ paddingBottom: '12px' }}>
              <div style={{ display: 'flex', gap: '16px' }}>
                <button
                  type="button"
                  style={{
                    fontSize: '16px',
                    fontWeight: '700',
                    color: authMode === 'login' ? 'white' : 'var(--text-muted)',
                    borderBottom: authMode === 'login' ? '2px solid var(--primary)' : '2px solid transparent',
                    paddingBottom: '8px',
                  }}
                  onClick={() => {
                    setAuthMode('login')
                    setAuthError('')
                  }}
                >
                  Sign In
                </button>

                <button
                  type="button"
                  style={{
                    fontSize: '16px',
                    fontWeight: '700',
                    color: authMode === 'register' ? 'white' : 'var(--text-muted)',
                    borderBottom: authMode === 'register' ? '2px solid var(--primary)' : '2px solid transparent',
                    paddingBottom: '8px',
                  }}
                  onClick={() => {
                    setAuthMode('register')
                    setAuthError('')
                  }}
                >
                  Create Account
                </button>
              </div>

              <button className="modal-close-btn" onClick={() => setShowLogin(false)}>✕</button>
            </div>

            <form onSubmit={handleAuth}>
              <div className="modal-body">
                {authError && (
                  <div className="error-banner" style={{ marginBottom: '16px' }}>
                    <span>{authError}</span>
                  </div>
                )}

                {authSuccess && (
                  <div className="security-warning-notice" style={{ color: '#34d399', background: 'rgba(16, 185, 129, 0.1)', borderColor: 'rgba(16, 185, 129, 0.25)', marginBottom: '16px' }}>
                    <span>✓</span>
                    <span>{authSuccess}</span>
                  </div>
                )}

                {authMode === 'register' && (
                  <>
                    <div className="form-group">
                      <label className="form-label">Full Name</label>
                      <input
                        type="text"
                        required
                        className="form-input"
                        placeholder="John Doe"
                        value={fullName}
                        onChange={(e) => setFullName(e.target.value)}
                      />
                    </div>

                    <div className="form-group">
                      <label className="form-label">Username</label>
                      <input
                        type="text"
                        required
                        className="form-input"
                        placeholder="johndoe"
                        value={username}
                        onChange={(e) => setUsername(e.target.value)}
                      />
                    </div>

                    <div className="form-group">
                      <label className="form-label">Email Address</label>
                      <input
                        type="email"
                        required
                        className="form-input"
                        placeholder="john@example.com"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                      />
                    </div>
                  </>
                )}

                {authMode === 'login' && (
                  <div className="form-group">
                    <label className="form-label">Username or Email</label>
                    <input
                      type="text"
                      required
                      className="form-input"
                      placeholder="Username or email address"
                      value={usernameOrEmail}
                      onChange={(e) => setUsernameOrEmail(e.target.value)}
                    />
                  </div>
                )}

                <div className="form-group">
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '7px' }}>
                    <label className="form-label" style={{ marginBottom: 0 }}>Password</label>
                    <button
                      type="button"
                      style={{ fontSize: '11px', color: 'var(--text-muted)' }}
                      onClick={() => setShowPassword(!showPassword)}
                    >
                      {showPassword ? 'Hide' : 'Show'}
                    </button>
                  </div>
                  <input
                    type={showPassword ? 'text' : 'password'}
                    required
                    className="form-input"
                    placeholder="••••••••••••"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                  />
                </div>
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  className="btn-ghost"
                  onClick={() => setShowLogin(false)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn-primary-gradient"
                  style={{ width: '100%', justifyContent: 'center' }}
                  disabled={authLoading}
                >
                  {authLoading
                    ? 'Authenticating...'
                    : authMode === 'login'
                    ? 'Sign In to StrataLive'
                    : 'Create Free Account'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* E. Profile Modal */}
      {showProfileModal && currentUser && (
        <div className="modal-backdrop" onClick={() => setShowProfileModal(false)}>
          <div className="modal-dialog" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '420px' }}>
            <div className="modal-header">
              <h3 className="modal-title">Creator Profile</h3>
              <button className="modal-close-btn" onClick={() => setShowProfileModal(false)}>✕</button>
            </div>

            <div className="modal-body" style={{ textAlign: 'center', padding: '32px 28px' }}>
              <div
                className="user-avatar-circle"
                style={{ width: '64px', height: '64px', fontSize: '24px', margin: '0 auto 16px' }}
              >
                {userInitial}
              </div>

              <h2 style={{ fontSize: '20px', fontWeight: '700', marginBottom: '4px' }}>
                {currentUser.fullName || currentUser.username}
              </h2>
              <div style={{ color: 'var(--text-muted)', fontSize: '14px', marginBottom: '18px' }}>
                @{currentUser.username} • {currentUser.email}
              </div>

              <div style={{ display: 'inline-block', padding: '4px 12px', background: 'rgba(99, 102, 241, 0.15)', border: '1px solid rgba(99, 102, 241, 0.3)', borderRadius: 'var(--radius-full)', fontSize: '12px', color: 'var(--primary-light)', fontWeight: '600', marginBottom: '24px' }}>
                Role: {currentUser.role || 'ROLE_USER'}
              </div>

              <div style={{ borderTop: '1px solid var(--border-subtle)', paddingTop: '18px', display: 'flex', justifyContent: 'space-between', color: 'var(--text-muted)', fontSize: '13px' }}>
                <span>Account Created:</span>
                <span style={{ color: 'var(--text-primary)' }}>
                  {currentUser.createdAt ? new Date(currentUser.createdAt).toLocaleDateString() : 'Active'}
                </span>
              </div>
            </div>

            <div className="modal-footer" style={{ justifyContent: 'center' }}>
              <button
                className="btn-ghost"
                style={{ color: '#f87171', width: '100%', justifyContent: 'center' }}
                onClick={handleLogout}
              >
                Sign Out of Account
              </button>
            </div>
          </div>
        </div>
      )}

      {/* F. Quick Encoder Setup Guide Modal */}
      {showEncoderModal && (
        <div className="modal-backdrop" onClick={() => setShowEncoderModal(false)}>
          <div className="modal-dialog" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '580px' }}>
            <div className="modal-header">
              <h3 className="modal-title">⚡ Quick Encoder Setup</h3>
              <button className="modal-close-btn" onClick={() => setShowEncoderModal(false)}>✕</button>
            </div>

            <div className="modal-body">
              <p style={{ color: 'var(--text-secondary)', fontSize: '14px', marginBottom: '20px' }}>
                Configure OBS Studio, FFmpeg, or any RTMP-compatible encoder to broadcast directly to StrataLive.
              </p>

              <div className="credential-field">
                <span className="credential-label">1. RTMP Ingestion Server</span>
                <div className="credential-input-bar">
                  <span className="credential-value">{RTMP_SERVER_BASE}</span>
                  <button
                    className="btn-mini"
                    onClick={() => copyToClipboard(RTMP_SERVER_BASE, 'Server URL copied!')}
                  >
                    Copy
                  </button>
                </div>
              </div>

              <div className="credential-field">
                <span className="credential-label">2. OBS Studio Configuration</span>
                <div style={{ background: '#0a0d18', padding: '12px 14px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', fontSize: '13px', color: 'var(--text-secondary)', lineHeight: '1.5' }}>
                  • Open <strong>OBS Settings</strong> → <strong>Stream</strong><br />
                  • Service: <strong>Custom...</strong><br />
                  • Server: <code style={{ color: 'var(--accent-cyan)' }}>{RTMP_SERVER_BASE}</code><br />
                  • Stream Key: <em>(Copy from your channel details)</em>
                </div>
              </div>

              <div className="credential-field" style={{ marginBottom: 0 }}>
                <span className="credential-label">3. FFmpeg Command Template</span>
                <div className="credential-input-bar">
                  <span className="credential-value" style={{ fontSize: '11px' }}>
                    {`ffmpeg -re -i input.mp4 -c:v libx264 -preset veryfast -c:a aac -f flv "${RTMP_SERVER_BASE}/<STREAM_KEY>"`}
                  </span>
                  <button
                    className="btn-mini"
                    onClick={() => copyToClipboard(`ffmpeg -re -i input.mp4 -c:v libx264 -preset veryfast -c:a aac -f flv "${RTMP_SERVER_BASE}/<STREAM_KEY>"`, 'FFmpeg command copied!')}
                  >
                    Copy
                  </button>
                </div>
              </div>
            </div>

            <div className="modal-footer">
              <button className="btn-primary-gradient" onClick={() => setShowEncoderModal(false)}>
                Done
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Upload Video Modal */}
      {showUploadVideo && (
        <div className="modal-backdrop" onClick={() => !uploadVideoLoading && setShowUploadVideo(false)}>
          <div className="modal-dialog" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="modal-title">Upload Video to StrataLive</h3>
              <button
                className="modal-close-btn"
                disabled={uploadVideoLoading}
                onClick={() => setShowUploadVideo(false)}
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleUploadVideo}>
              <div className="modal-body">
                {uploadVideoError && (
                  <div className="error-banner" style={{ marginBottom: '16px' }}>
                    <span>{uploadVideoError}</span>
                  </div>
                )}

                {/* Dropzone File Selector */}
                <div className="form-group">
                  <label className="form-label">Video File *</label>
                  <label
                    htmlFor="video-file-input"
                    className={`upload-dropzone ${uploadFile ? 'has-file' : ''}`}
                  >
                    <div className="upload-dropzone-icon">📁</div>
                    <div className="upload-dropzone-title">
                      {uploadFile ? uploadFile.name : 'Click to select or drag video file here'}
                    </div>
                    <div className="upload-dropzone-hint">
                      MP4, WebM, MKV, MOV (up to 500MB)
                    </div>
                    <input
                      id="video-file-input"
                      type="file"
                      accept="video/*,.mp4,.webm,.mkv,.mov"
                      style={{ display: 'none' }}
                      disabled={uploadVideoLoading}
                      onChange={(e) => {
                        if (e.target.files && e.target.files[0]) {
                          const file = e.target.files[0]
                          setUploadFile(file)
                          if (!uploadTitle) {
                            const nameWithoutExt = file.name.replace(/\.[^/.]+$/, '')
                            setUploadTitle(nameWithoutExt)
                          }
                        }
                      }}
                    />
                  </label>

                  {uploadFile && (
                    <div className="upload-file-selected">
                      <span>🎬</span>
                      <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {uploadFile.name} ({(uploadFile.size / (1024 * 1024)).toFixed(2)} MB)
                      </span>
                      {!uploadVideoLoading && (
                        <button
                          type="button"
                          className="btn-mini"
                          onClick={() => setUploadFile(null)}
                        >
                          Change
                        </button>
                      )}
                    </div>
                  )}
                </div>

                <div className="form-group">
                  <label className="form-label">Video Title *</label>
                  <input
                    type="text"
                    required
                    className="form-input"
                    placeholder="Enter an engaging title..."
                    value={uploadTitle}
                    disabled={uploadVideoLoading}
                    onChange={(e) => setUploadTitle(e.target.value)}
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">Category</label>
                  <select
                    className="form-select"
                    value={uploadCategory}
                    disabled={uploadVideoLoading}
                    onChange={(e) => setUploadCategory(e.target.value)}
                  >
                    {CATEGORIES.filter((c) => c !== 'All' && c !== 'Live').map((c) => (
                      <option key={c} value={c}>{c}</option>
                    ))}
                  </select>
                </div>

                <div className="form-group">
                  <label className="form-label">Description</label>
                  <textarea
                    className="form-textarea"
                    placeholder="Describe this video's contents..."
                    value={uploadDescription}
                    disabled={uploadVideoLoading}
                    onChange={(e) => setUploadDescription(e.target.value)}
                  />
                </div>

                <div className="form-group">
                  <div
                    className="form-toggle-label"
                    onClick={() => !uploadVideoLoading && setUploadIsPublic(!uploadIsPublic)}
                  >
                    <input
                      type="checkbox"
                      checked={uploadIsPublic}
                      disabled={uploadVideoLoading}
                      onChange={() => {}}
                    />
                    <div>
                      <div style={{ fontWeight: 600, fontSize: '13px' }}>Public Video</div>
                      <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                        Visible in the public video catalog for all visitors
                      </div>
                    </div>
                  </div>
                </div>

                {/* Upload Progress Bar */}
                {uploadVideoLoading && (
                  <div className="upload-progress-wrap">
                    <div className="upload-progress-header">
                      <span>Uploading to MinIO Storage...</span>
                      <span>{uploadVideoProgress}%</span>
                    </div>
                    <div className="upload-progress-bar-track">
                      <div
                        className="upload-progress-bar-fill"
                        style={{ width: `${uploadVideoProgress}%` }}
                      />
                    </div>
                  </div>
                )}
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  className="btn-ghost"
                  disabled={uploadVideoLoading}
                  onClick={() => setShowUploadVideo(false)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn-primary-gradient"
                  disabled={uploadVideoLoading || !uploadFile}
                >
                  {uploadVideoLoading
                    ? `Uploading (${uploadVideoProgress}%)...`
                    : 'Upload to MinIO'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Edit Video Modal */}
      {showEditVideo && (
        <div className="modal-backdrop" onClick={() => setShowEditVideo(false)}>
          <div className="modal-dialog" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="modal-title">Edit Video Details</h3>
              <button className="modal-close-btn" onClick={() => setShowEditVideo(false)}>✕</button>
            </div>

            <form onSubmit={handleUpdateVideo}>
              <div className="modal-body">
                {editVideoError && (
                  <div className="error-banner" style={{ marginBottom: '16px' }}>
                    <span>{editVideoError}</span>
                  </div>
                )}

                <div className="form-group">
                  <label className="form-label">Video Title *</label>
                  <input
                    type="text"
                    required
                    className="form-input"
                    value={editVideoTitle}
                    onChange={(e) => setEditVideoTitle(e.target.value)}
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">Category</label>
                  <select
                    className="form-select"
                    value={editVideoCategory}
                    onChange={(e) => setEditVideoCategory(e.target.value)}
                  >
                    {CATEGORIES.filter((c) => c !== 'All' && c !== 'Live').map((c) => (
                      <option key={c} value={c}>{c}</option>
                    ))}
                  </select>
                </div>

                <div className="form-group">
                  <label className="form-label">Description</label>
                  <textarea
                    className="form-textarea"
                    value={editVideoDescription}
                    onChange={(e) => setEditVideoDescription(e.target.value)}
                  />
                </div>

                <div className="form-group">
                  <div
                    className="form-toggle-label"
                    onClick={() => setEditVideoIsPublic(!editVideoIsPublic)}
                  >
                    <input
                      type="checkbox"
                      checked={editVideoIsPublic}
                      onChange={() => {}}
                    />
                    <div>
                      <div style={{ fontWeight: 600, fontSize: '13px' }}>Public Video</div>
                      <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                        Visible in the public video catalog
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  className="btn-ghost"
                  disabled={editVideoLoading}
                  onClick={() => setShowEditVideo(false)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn-primary-gradient"
                  disabled={editVideoLoading}
                >
                  {editVideoLoading ? 'Saving...' : 'Save Changes'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete Video Confirmation Modal */}
      {showDeleteVideoConfirm && videoToDelete && (
        <div className="modal-backdrop" onClick={() => setShowDeleteVideoConfirm(false)}>
          <div className="modal-dialog" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="modal-title">Delete Video</h3>
              <button className="modal-close-btn" onClick={() => setShowDeleteVideoConfirm(false)}>✕</button>
            </div>

            <div className="modal-body">
              {deleteVideoError && (
                <div className="error-banner" style={{ marginBottom: '16px' }}>
                  <span>{deleteVideoError}</span>
                </div>
              )}

              <p style={{ color: 'var(--text-secondary)', fontSize: '14px', lineHeight: '1.6' }}>
                Are you sure you want to delete <strong style={{ color: 'white' }}>"{videoToDelete.title}"</strong>?
              </p>
              <p style={{ color: '#f87171', fontSize: '13px', marginTop: '8px' }}>
                ⚠️ This action will permanently remove this video record from the database and delete the stored file from MinIO. This action cannot be undone.
              </p>
            </div>

            <div className="modal-footer">
              <button
                type="button"
                className="btn-ghost"
                disabled={deleteVideoLoading}
                onClick={() => setShowDeleteVideoConfirm(false)}
              >
                Cancel
              </button>
              <button
                type="button"
                className="btn-primary-gradient"
                style={{ background: 'linear-gradient(135deg, #ef4444 0%, #b91c1c 100%)' }}
                disabled={deleteVideoLoading}
                onClick={confirmDeleteVideo}
              >
                {deleteVideoLoading ? 'Deleting...' : 'Confirm Delete'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Floating Action Toast Notification */}
      {toastMessage && (
        <div className="toast-notification">
          <span>✓</span>
          <span>{toastMessage}</span>
        </div>
      )}
    </div>
  )
}

export default App

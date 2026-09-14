import { useEffect, useState } from 'react'

function formatDuration(seconds) {
  if (!seconds || seconds <= 0) return '0m'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  return h > 0 ? `${h}h ${m}m` : `${m}m`
}

function formatBytes(bytes) {
  if (!bytes || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let b = bytes
  let i = 0
  while (b >= 1024 && i < units.length - 1) {
    b /= 1024
    i++
  }
  return `${b.toFixed(1)} ${units[i]}`
}

export default function AdminDashboardView({ token, apiBaseUrl, currentUser, showToast }) {
  const [activeTab, setActiveTab] = useState('overview') // 'overview' | 'users' | 'streams' | 'videos'

  // Data states
  const [stats, setStats] = useState(null)
  const [users, setUsers] = useState([])
  const [streams, setStreams] = useState([])
  const [videos, setVideos] = useState([])

  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  // Search filter
  const [search, setSearch] = useState('')

  // Confirmation modal state
  const [confirmModal, setConfirmModal] = useState({
    isOpen: false,
    title: '',
    message: '',
    confirmText: 'Confirm',
    isDanger: false,
    onConfirm: null,
  })
  const [actionLoading, setActionLoading] = useState(false)
  const [refreshTrigger, setRefreshTrigger] = useState(0)

  useEffect(() => {
    let isMounted = true

    const load = async () => {
      setLoading(true)
      setError('')
      try {
        if (activeTab === 'overview') {
          const res = await fetch(`${apiBaseUrl}/api/admin/stats`, {
            headers: { Accept: 'application/json', Authorization: `Bearer ${token}` },
          })
          if (res.ok && isMounted) setStats(await res.json())
        } else if (activeTab === 'users') {
          const res = await fetch(`${apiBaseUrl}/api/admin/users`, {
            headers: { Accept: 'application/json', Authorization: `Bearer ${token}` },
          })
          if (res.ok && isMounted) setUsers(await res.json())
        } else if (activeTab === 'streams') {
          const res = await fetch(`${apiBaseUrl}/api/admin/streams`, {
            headers: { Accept: 'application/json', Authorization: `Bearer ${token}` },
          })
          if (res.ok && isMounted) setStreams(await res.json())
        } else if (activeTab === 'videos') {
          const res = await fetch(`${apiBaseUrl}/api/admin/videos`, {
            headers: { Accept: 'application/json', Authorization: `Bearer ${token}` },
          })
          if (res.ok && isMounted) setVideos(await res.json())
        }
      } catch (err) {
        if (isMounted) setError(err.message || 'Failed to load admin data')
      } finally {
        if (isMounted) setLoading(false)
      }
    }

    load()

    return () => {
      isMounted = false
    }
  }, [activeTab, token, apiBaseUrl, refreshTrigger])

  // Admin Actions
  const handleToggleUserStatus = (user) => {
    const newStatus = !user.enabled
    setConfirmModal({
      isOpen: true,
      title: `${newStatus ? 'Activate' : 'Deactivate'} User Account`,
      message: `Are you sure you want to ${newStatus ? 'activate' : 'deactivate'} @${user.username}? ${newStatus ? 'They will regain access to the platform.' : 'They will be prevented from logging in.'}`,
      confirmText: newStatus ? 'Activate Account' : 'Deactivate Account',
      isDanger: !newStatus,
      onConfirm: async () => {
        setActionLoading(true)
        try {
          const res = await fetch(`${apiBaseUrl}/api/admin/users/${user.id}/status`, {
            method: 'PUT',
            headers: {
              'Content-Type': 'application/json',
              Accept: 'application/json',
              Authorization: `Bearer ${token}`,
            },
            body: JSON.stringify({ enabled: newStatus }),
          })
          if (!res.ok) {
            const txt = await res.text()
            throw new Error(txt || 'Action failed')
          }
          showToast(`User @${user.username} successfully ${newStatus ? 'activated' : 'deactivated'}.`)
          setRefreshTrigger((t) => t + 1)
          setConfirmModal({ isOpen: false })
        } catch (err) {
          showToast(`Failed: ${err.message}`)
        } finally {
          setActionLoading(false)
        }
      },
    })
  }

  const handleToggleUserRole = (user) => {
    const newRole = (user.role === 'ADMIN' || user.role === 'ROLE_ADMIN') ? 'USER' : 'ADMIN'
    setConfirmModal({
      isOpen: true,
      title: `Change User Role to ${newRole}`,
      message: `Are you sure you want to change @${user.username}'s permission tier to ${newRole}? ${newRole === 'ADMIN' ? 'This grants complete administrative privileges.' : 'This restricts access to standard creator features.'}`,
      confirmText: `Set Role to ${newRole}`,
      isDanger: newRole === 'ADMIN',
      onConfirm: async () => {
        setActionLoading(true)
        try {
          const res = await fetch(`${apiBaseUrl}/api/admin/users/${user.id}/role`, {
            method: 'PUT',
            headers: {
              'Content-Type': 'application/json',
              Accept: 'application/json',
              Authorization: `Bearer ${token}`,
            },
            body: JSON.stringify({ role: newRole }),
          })
          if (!res.ok) {
            const txt = await res.text()
            throw new Error(txt || 'Action failed')
          }
          showToast(`User @${user.username} role updated to ${newRole}.`)
          setRefreshTrigger((t) => t + 1)
          setConfirmModal({ isOpen: false })
        } catch (err) {
          showToast(`Failed: ${err.message}`)
        } finally {
          setActionLoading(false)
        }
      },
    })
  }

  const handleDeleteStream = (stream) => {
    setConfirmModal({
      isOpen: true,
      title: 'Moderate & Delete Broadcast',
      message: `Are you sure you want to permanently terminate and remove the stream "${stream.title}"? Any active play sessions will be disconnected immediately.`,
      confirmText: 'Delete Broadcast',
      isDanger: true,
      onConfirm: async () => {
        setActionLoading(true)
        try {
          const res = await fetch(`${apiBaseUrl}/api/admin/streams/${stream.id}`, {
            method: 'DELETE',
            headers: { Authorization: `Bearer ${token}` },
          })
          if (!res.ok) throw new Error('Failed to delete stream')
          showToast(`Broadcast "${stream.title}" deleted.`)
          setRefreshTrigger((t) => t + 1)
          setConfirmModal({ isOpen: false })
        } catch (err) {
          showToast(`Failed: ${err.message}`)
        } finally {
          setActionLoading(false)
        }
      },
    })
  }

  const handleDeleteVideo = (video) => {
    setConfirmModal({
      isOpen: true,
      title: 'Moderate & Delete Video',
      message: `Are you sure you want to permanently delete the video "${video.title}"? The media file will be purged from MinIO object storage.`,
      confirmText: 'Delete Video',
      isDanger: true,
      onConfirm: async () => {
        setActionLoading(true)
        try {
          const res = await fetch(`${apiBaseUrl}/api/admin/videos/${video.id}`, {
            method: 'DELETE',
            headers: { Authorization: `Bearer ${token}` },
          })
          if (!res.ok) throw new Error('Failed to delete video')
          showToast(`Video "${video.title}" deleted from storage and database.`)
          setRefreshTrigger((t) => t + 1)
          setConfirmModal({ isOpen: false })
        } catch (err) {
          showToast(`Failed: ${err.message}`)
        } finally {
          setActionLoading(false)
        }
      },
    })
  }

  // Filter lists
  const filteredUsers = users.filter((u) => {
    if (!search) return true
    const q = search.toLowerCase()
    return (
      (u.username && u.username.toLowerCase().includes(q)) ||
      (u.fullName && u.fullName.toLowerCase().includes(q)) ||
      (u.email && u.email.toLowerCase().includes(q))
    )
  })

  const filteredStreams = streams.filter((s) => {
    if (!search) return true
    const q = search.toLowerCase()
    return (
      (s.title && s.title.toLowerCase().includes(q)) ||
      (s.creatorUsername && s.creatorUsername.toLowerCase().includes(q)) ||
      (s.category && s.category.toLowerCase().includes(q))
    )
  })

  const filteredVideos = videos.filter((v) => {
    if (!search) return true
    const q = search.toLowerCase()
    return (
      (v.title && v.title.toLowerCase().includes(q)) ||
      (v.creatorUsername && v.creatorUsername.toLowerCase().includes(q)) ||
      (v.category && v.category.toLowerCase().includes(q))
    )
  })

  return (
    <div className="admin-page">
      <div className="section-heading-bar">
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span style={{ fontSize: '24px' }}>🛡️</span>
            <h1 className="section-title">StrataLive Governance & Admin Console</h1>
            <span className="badge-live-pill" style={{ background: 'rgba(239, 68, 68, 0.15)', borderColor: 'rgba(239, 68, 68, 0.4)', color: '#f87171' }}>
              ADMIN ACCESS
            </span>
          </div>
          <p style={{ color: 'var(--text-muted)', fontSize: '14px', marginTop: '4px' }}>
            Platform supervision, account management, and content moderation.
          </p>
        </div>

        <button className="btn-ghost" onClick={() => setRefreshTrigger((t) => t + 1)} disabled={loading}>
          <span>🔄</span>
          <span>{loading ? 'Refreshing...' : 'Refresh Data'}</span>
        </button>
      </div>

      {error && (
        <div className="error-banner" style={{ marginBottom: '20px' }}>
          <span>{error}</span>
          <button className="btn-ghost" onClick={() => setRefreshTrigger((t) => t + 1)} style={{ marginLeft: '12px' }}>Retry</button>
        </div>
      )}

      {/* Admin Subtabs */}
      <div className="admin-subtabs-bar">
        <button
          className={`admin-subtab-btn ${activeTab === 'overview' ? 'active' : ''}`}
          onClick={() => setActiveTab('overview')}
        >
          <span>📊</span>
          <span>Platform Overview</span>
        </button>

        <button
          className={`admin-subtab-btn ${activeTab === 'users' ? 'active' : ''}`}
          onClick={() => setActiveTab('users')}
        >
          <span>👥</span>
          <span>User Directory ({users.length})</span>
        </button>

        <button
          className={`admin-subtab-btn ${activeTab === 'streams' ? 'active' : ''}`}
          onClick={() => setActiveTab('streams')}
        >
          <span>📡</span>
          <span>Broadcast Moderation ({streams.length})</span>
        </button>

        <button
          className={`admin-subtab-btn ${activeTab === 'videos' ? 'active' : ''}`}
          onClick={() => setActiveTab('videos')}
        >
          <span>🎬</span>
          <span>VOD Moderation ({videos.length})</span>
        </button>
      </div>

      {/* VIEW 1: OVERVIEW */}
      {activeTab === 'overview' && (
        <div>
          {stats ? (
            <div className="analytics-kpi-grid">
              <div className="kpi-card">
                <div className="kpi-header">
                  <span className="kpi-title">Registered Accounts</span>
                  <span className="kpi-icon">👥</span>
                </div>
                <div className="kpi-value">{stats.totalUsers}</div>
                <div className="kpi-submeta">
                  <span style={{ color: '#10b981' }}>{stats.activeUsers} active</span> •{' '}
                  <span style={{ color: 'var(--text-muted)' }}>{stats.totalUsers - stats.activeUsers} disabled</span>
                </div>
              </div>

              <div className="kpi-card">
                <div className="kpi-header">
                  <span className="kpi-title">Broadcast Channels</span>
                  <span className="kpi-icon">📡</span>
                </div>
                <div className="kpi-value" style={{ color: 'var(--accent-cyan)' }}>{stats.totalStreams}</div>
                <div className="kpi-submeta">
                  <span style={{ color: '#ef4444' }}>{stats.liveStreams} live</span> •{' '}
                  <span style={{ color: '#818cf8' }}>{stats.scheduledStreams} scheduled</span> •{' '}
                  <span style={{ color: 'var(--text-muted)' }}>{stats.endedStreams} ended</span>
                </div>
              </div>

              <div className="kpi-card">
                <div className="kpi-header">
                  <span className="kpi-title">Peak Platform Viewers</span>
                  <span className="kpi-icon">👁️</span>
                </div>
                <div className="kpi-value" style={{ color: '#f59e0b' }}>{stats.maxConcurrentViewers}</div>
                <div className="kpi-submeta">Max concurrent live audience across all feeds</div>
              </div>

              <div className="kpi-card">
                <div className="kpi-header">
                  <span className="kpi-title">Cumulative Airtime</span>
                  <span className="kpi-icon">⏱️</span>
                </div>
                <div className="kpi-value" style={{ color: 'var(--primary-light)' }}>
                  {formatDuration(stats.totalStreamDurationSeconds)}
                </div>
                <div className="kpi-submeta">Platform-wide RTMP broadcast duration</div>
              </div>

              <div className="kpi-card">
                <div className="kpi-header">
                  <span className="kpi-title">VOD Media Files</span>
                  <span className="kpi-icon">📁</span>
                </div>
                <div className="kpi-value">{stats.totalVideos}</div>
                <div className="kpi-submeta">Total uploaded on-demand videos</div>
              </div>

              <div className="kpi-card">
                <div className="kpi-header">
                  <span className="kpi-title">Platform Video Plays</span>
                  <span className="kpi-icon">▶</span>
                </div>
                <div className="kpi-value" style={{ color: '#10b981' }}>{stats.totalVideoViews}</div>
                <div className="kpi-submeta">Total HTTP range streaming requests served</div>
              </div>

              <div className="kpi-card" style={{ gridColumn: 'span 2' }}>
                <div className="kpi-header">
                  <span className="kpi-title">MinIO Storage Footprint</span>
                  <span className="kpi-icon">💾</span>
                </div>
                <div className="kpi-value">{formatBytes(stats.totalStorageBytes)}</div>
                <div className="kpi-submeta">Active binary storage allocated in `stratalive-videos` bucket</div>
              </div>
            </div>
          ) : (
            <div className="empty-state-card">
              <p>Loading platform metrics...</p>
            </div>
          )}
        </div>
      )}

      {/* VIEW 2: USERS */}
      {activeTab === 'users' && (
        <div className="analytics-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px', gap: '16px' }}>
            <h3 style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary)' }}>
              Registered User Directory
            </h3>
            <input
              type="text"
              className="form-input"
              style={{ maxWidth: '280px', padding: '8px 12px', fontSize: '13px' }}
              placeholder="Search by username, name, email..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>

          <div className="analytics-table-wrapper">
            <table className="analytics-table">
              <thead>
                <tr>
                  <th>User</th>
                  <th>Email</th>
                  <th>Role</th>
                  <th>Status</th>
                  <th>Joined</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredUsers.map((u) => {
                  const isSelf = currentUser && currentUser.id === u.id
                  const isAdmin = u.role === 'ADMIN' || u.role === 'ROLE_ADMIN'
                  return (
                    <tr key={u.id}>
                      <td>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                          <div
                            style={{
                              width: '32px',
                              height: '32px',
                              borderRadius: '50%',
                              background: isAdmin ? 'linear-gradient(135deg, #ef4444, #f59e0b)' : 'linear-gradient(135deg, #6366f1, #06b6d4)',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              fontWeight: 700,
                              fontSize: '13px',
                              color: 'white',
                            }}
                          >
                            {(u.username || 'U')[0].toUpperCase()}
                          </div>
                          <div>
                            <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>
                              {u.fullName || u.username} {isSelf && <span style={{ fontSize: '11px', color: 'var(--accent-cyan)' }}>(You)</span>}
                            </div>
                            <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>@{u.username}</div>
                          </div>
                        </div>
                      </td>
                      <td style={{ color: 'var(--text-secondary)' }}>{u.email}</td>
                      <td>
                        {isAdmin ? (
                          <span className="badge-live-pill" style={{ background: 'rgba(239, 68, 68, 0.12)', color: '#f87171' }}>
                            ADMIN
                          </span>
                        ) : (
                          <span className="badge-offline-pill">USER</span>
                        )}
                      </td>
                      <td>
                        {u.enabled ? (
                          <span className="badge-scheduled-pill" style={{ background: 'rgba(16, 185, 129, 0.12)', color: '#34d399', borderColor: 'rgba(16, 185, 129, 0.3)' }}>
                            Active
                          </span>
                        ) : (
                          <span className="badge-ended-pill">Disabled</span>
                        )}
                      </td>
                      <td style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                        {new Date(u.createdAt).toLocaleDateString()}
                      </td>
                      <td>
                        <div style={{ display: 'flex', gap: '8px' }}>
                          <button
                            className="btn-ghost"
                            style={{ padding: '4px 8px', fontSize: '12px' }}
                            disabled={isSelf}
                            title={isSelf ? 'Cannot modify your own account' : ''}
                            onClick={() => handleToggleUserStatus(u)}
                          >
                            {u.enabled ? 'Deactivate' : 'Activate'}
                          </button>
                          <button
                            className="btn-ghost"
                            style={{ padding: '4px 8px', fontSize: '12px' }}
                            disabled={isSelf}
                            title={isSelf ? 'Cannot demote your own account' : ''}
                            onClick={() => handleToggleUserRole(u)}
                          >
                            {isAdmin ? 'Demote' : 'Make Admin'}
                          </button>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* VIEW 3: STREAMS */}
      {activeTab === 'streams' && (
        <div className="analytics-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px', gap: '16px' }}>
            <h3 style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary)' }}>
              Broadcast Moderation
            </h3>
            <input
              type="text"
              className="form-input"
              style={{ maxWidth: '280px', padding: '8px 12px', fontSize: '13px' }}
              placeholder="Search streams or creator..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>

          <div className="analytics-table-wrapper">
            <table className="analytics-table">
              <thead>
                <tr>
                  <th>Stream Channel</th>
                  <th>Creator</th>
                  <th>Category</th>
                  <th>Status</th>
                  <th>Viewers</th>
                  <th>Created / Scheduled</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredStreams.map((s) => (
                  <tr key={s.id}>
                    <td>
                      <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{s.title}</div>
                      <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>ID #{s.id}</div>
                    </td>
                    <td>
                      <span style={{ color: 'var(--text-secondary)' }}>@{s.creatorUsername || 'creator'}</span>
                    </td>
                    <td>
                      <span className="category-tag">{s.category}</span>
                    </td>
                    <td>
                      {s.status === 'LIVE' ? (
                        <span className="badge-live-pill">🔴 LIVE</span>
                      ) : s.status === 'SCHEDULED' ? (
                        <span className="badge-scheduled-pill">📅 SCHEDULED</span>
                      ) : s.status === 'ENDED' ? (
                        <span className="badge-ended-pill">ENDED</span>
                      ) : (
                        <span className="badge-offline-pill">OFFLINE</span>
                      )}
                    </td>
                    <td>
                      <strong style={{ color: s.viewerCount > 0 ? 'var(--accent-cyan)' : 'var(--text-muted)' }}>
                        {s.viewerCount || 0}
                      </strong>
                    </td>
                    <td style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                      {s.scheduledStartTime
                        ? `Starts: ${new Date(s.scheduledStartTime).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' })}`
                        : new Date(s.createdAt).toLocaleDateString()}
                    </td>
                    <td>
                      <button
                        className="btn-danger"
                        style={{ padding: '4px 10px', fontSize: '12px', borderRadius: 'var(--radius-xs)', background: 'rgba(239, 68, 68, 0.15)', color: '#f87171', border: '1px solid rgba(239, 68, 68, 0.3)' }}
                        onClick={() => handleDeleteStream(s)}
                      >
                        Delete Stream
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* VIEW 4: VIDEOS */}
      {activeTab === 'videos' && (
        <div className="analytics-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px', gap: '16px' }}>
            <h3 style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text-primary)' }}>
              VOD Media Moderation
            </h3>
            <input
              type="text"
              className="form-input"
              style={{ maxWidth: '280px', padding: '8px 12px', fontSize: '13px' }}
              placeholder="Search videos or creator..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>

          <div className="analytics-table-wrapper">
            <table className="analytics-table">
              <thead>
                <tr>
                  <th>Video Title</th>
                  <th>Creator</th>
                  <th>Category</th>
                  <th>Views</th>
                  <th>Size</th>
                  <th>Upload Date</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredVideos.map((v) => (
                  <tr key={v.id}>
                    <td>
                      <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{v.title}</div>
                      <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{v.originalFilename}</div>
                    </td>
                    <td>
                      <span style={{ color: 'var(--text-secondary)' }}>@{v.creatorUsername || 'creator'}</span>
                    </td>
                    <td>
                      <span className="category-tag">{v.category}</span>
                    </td>
                    <td>
                      <strong style={{ color: v.views > 0 ? '#10b981' : 'var(--text-muted)' }}>
                        {v.views || 0}
                      </strong>
                    </td>
                    <td style={{ color: 'var(--text-secondary)' }}>
                      {formatBytes(v.fileSize)}
                    </td>
                    <td style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                      {new Date(v.createdAt).toLocaleDateString()}
                    </td>
                    <td>
                      <button
                        className="btn-danger"
                        style={{ padding: '4px 10px', fontSize: '12px', borderRadius: 'var(--radius-xs)', background: 'rgba(239, 68, 68, 0.15)', color: '#f87171', border: '1px solid rgba(239, 68, 68, 0.3)' }}
                        onClick={() => handleDeleteVideo(v)}
                      >
                        Delete Video
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Confirmation Dialog Modal */}
      {confirmModal.isOpen && (
        <div className="modal-backdrop" onClick={() => !actionLoading && setConfirmModal({ isOpen: false })}>
          <div className="modal-dialog" style={{ maxWidth: '440px' }} onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="modal-title">{confirmModal.title}</h3>
              <button
                className="modal-close-btn"
                disabled={actionLoading}
                onClick={() => setConfirmModal({ isOpen: false })}
              >
                ✕
              </button>
            </div>
            <div className="modal-body">
              <p style={{ color: 'var(--text-secondary)', fontSize: '14px', lineHeight: '1.5' }}>
                {confirmModal.message}
              </p>
            </div>
            <div className="modal-footer">
              <button
                type="button"
                className="btn-ghost"
                disabled={actionLoading}
                onClick={() => setConfirmModal({ isOpen: false })}
              >
                Cancel
              </button>
              <button
                type="button"
                className={confirmModal.isDanger ? 'btn-danger' : 'btn-primary-gradient'}
                style={confirmModal.isDanger ? { background: '#dc2626', color: 'white', border: 'none', padding: '8px 16px', borderRadius: 'var(--radius-sm)' } : {}}
                disabled={actionLoading}
                onClick={confirmModal.onConfirm}
              >
                {actionLoading ? 'Processing...' : confirmModal.confirmText}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

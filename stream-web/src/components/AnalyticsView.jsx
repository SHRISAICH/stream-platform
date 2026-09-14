import { useEffect, useState } from 'react'

function formatDuration(seconds) {
  if (!seconds || seconds <= 0) return '0m'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  if (h > 0) return `${h}h ${m}m`
  if (m > 0) return `${m}m ${s}s`
  return `${s}s`
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

export default function AnalyticsView({ token, apiBaseUrl, onOpenStream }) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [refreshTrigger, setRefreshTrigger] = useState(0)

  useEffect(() => {
    let isMounted = true
    const load = async () => {
      setLoading(true)
      setError('')
      try {
        const res = await fetch(`${apiBaseUrl}/api/analytics/creator`, {
          headers: {
            Accept: 'application/json',
            Authorization: `Bearer ${token}`,
          },
        })
        if (!res.ok) {
          throw new Error(`Failed to load analytics: ${res.status}`)
        }
        const json = await res.json()
        if (isMounted) setData(json)
      } catch (err) {
        if (isMounted) setError(err.message || 'Error loading analytics.')
      } finally {
        if (isMounted) setLoading(false)
      }
    }
    load()
    return () => {
      isMounted = false
    }
  }, [token, apiBaseUrl, refreshTrigger])

  const catColors = ['#6366f1', '#06b6d4', '#10b981', '#f59e0b', '#ec4899', '#8b5cf6', '#3b82f6']

  return (
    <div className="analytics-page">
      <div className="section-heading-bar">
        <div>
          <h1 className="section-title">Creator Analytics Dashboard</h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '14px', marginTop: '4px' }}>
            Real-time telemetry and audience statistics derived directly from SRS media and VOD sessions.
          </p>
        </div>

        <button
          className="btn-ghost"
          onClick={() => setRefreshTrigger((t) => t + 1)}
          disabled={loading}
          style={{ display: 'flex', alignItems: 'center', gap: '8px' }}
        >
          <span>🔄</span>
          <span>{loading ? 'Refreshing...' : 'Refresh Metrics'}</span>
        </button>
      </div>

      {error && (
        <div className="error-banner" style={{ marginBottom: '20px' }}>
          <span>{error}</span>
          <button className="btn-ghost" onClick={() => setRefreshTrigger((t) => t + 1)} style={{ marginLeft: '12px', padding: '4px 10px' }}>Retry</button>
        </div>
      )}

      {loading && !data ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '16px', marginBottom: '28px' }}>
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <div key={i} className="skeleton-card" style={{ height: '110px' }} />
          ))}
        </div>
      ) : data ? (
        <>
          {/* KPI Cards Grid */}
          <div className="analytics-kpi-grid">
            <div className="kpi-card">
              <div className="kpi-header">
                <span className="kpi-title">Total Broadcasts</span>
                <span className="kpi-icon">📹</span>
              </div>
              <div className="kpi-value">{data.totalStreams}</div>
              <div className="kpi-submeta">
                <span style={{ color: '#10b981' }}>{data.liveStreams} live</span> •{' '}
                <span style={{ color: '#818cf8' }}>{data.scheduledStreams} scheduled</span> •{' '}
                <span style={{ color: 'var(--text-muted)' }}>{data.completedStreams} ended</span>
              </div>
            </div>

            <div className="kpi-card">
              <div className="kpi-header">
                <span className="kpi-title">Peak Live Viewers</span>
                <span className="kpi-icon">👁️</span>
              </div>
              <div className="kpi-value" style={{ color: 'var(--accent-cyan)' }}>{data.peakConcurrentViewers}</div>
              <div className="kpi-submeta">Highest concurrent audience recorded</div>
            </div>

            <div className="kpi-card">
              <div className="kpi-header">
                <span className="kpi-title">Broadcast Airtime</span>
                <span className="kpi-icon">⏱️</span>
              </div>
              <div className="kpi-value" style={{ color: 'var(--primary-light)' }}>{formatDuration(data.totalDurationSeconds)}</div>
              <div className="kpi-submeta">Cumulative live transmission time</div>
            </div>

            <div className="kpi-card">
              <div className="kpi-header">
                <span className="kpi-title">VOD Library</span>
                <span className="kpi-icon">🎬</span>
              </div>
              <div className="kpi-value">{data.totalVideos}</div>
              <div className="kpi-submeta">Published on-demand videos</div>
            </div>

            <div className="kpi-card">
              <div className="kpi-header">
                <span className="kpi-title">Video Plays</span>
                <span className="kpi-icon">▶</span>
              </div>
              <div className="kpi-value" style={{ color: '#10b981' }}>{data.totalVideoViews}</div>
              <div className="kpi-submeta">Direct video streaming requests</div>
            </div>

            <div className="kpi-card">
              <div className="kpi-header">
                <span className="kpi-title">MinIO Storage Used</span>
                <span className="kpi-icon">💾</span>
              </div>
              <div className="kpi-value">{formatBytes(data.totalStorageBytes)}</div>
              <div className="kpi-submeta">Persistent object storage consumption</div>
            </div>
          </div>

          {/* Category Distribution Breakdown */}
          {data.categoryDistribution && Object.keys(data.categoryDistribution).length > 0 && (
            <div className="analytics-card" style={{ marginBottom: '24px' }}>
              <h3 style={{ fontSize: '16px', fontWeight: 600, marginBottom: '14px', color: 'var(--text-primary)' }}>
                Content Category Distribution
              </h3>
              
              {/* Stacked bar */}
              <div style={{ display: 'flex', height: '14px', borderRadius: '8px', overflow: 'hidden', background: 'rgba(255,255,255,0.06)', marginBottom: '16px' }}>
                {(() => {
                  const total = Object.values(data.categoryDistribution).reduce((a, b) => a + b, 0)
                  return Object.entries(data.categoryDistribution).map(([cat, count], idx) => {
                    const pct = total > 0 ? (count / total) * 100 : 0
                    return (
                      <div
                        key={cat}
                        title={`${cat}: ${count} (${pct.toFixed(1)}%)`}
                        style={{
                          width: `${pct}%`,
                          backgroundColor: catColors[idx % catColors.length],
                          transition: 'width 0.4s ease',
                        }}
                      />
                    )
                  })
                })()}
              </div>

              {/* Legend */}
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '16px' }}>
                {Object.entries(data.categoryDistribution).map(([cat, count], idx) => (
                  <div key={cat} style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px' }}>
                    <span
                      style={{
                        width: '10px',
                        height: '10px',
                        borderRadius: '50%',
                        backgroundColor: catColors[idx % catColors.length],
                      }}
                    />
                    <span style={{ color: 'var(--text-secondary)' }}>{cat}:</span>
                    <strong style={{ color: 'var(--text-primary)' }}>{count}</strong>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Recent Streams Activity Table */}
          <div className="analytics-card">
            <h3 style={{ fontSize: '16px', fontWeight: 600, marginBottom: '16px', color: 'var(--text-primary)' }}>
              Recent Transmission Performance
            </h3>

            {(!data.recentStreams || data.recentStreams.length === 0) ? (
              <div className="empty-state-card" style={{ padding: '32px' }}>
                <p style={{ color: 'var(--text-muted)' }}>No broadcast records found. Start streaming to generate analytics data.</p>
              </div>
            ) : (
              <div className="analytics-table-wrapper">
                <table className="analytics-table">
                  <thead>
                    <tr>
                      <th>Broadcast Channel</th>
                      <th>Category</th>
                      <th>Status</th>
                      <th>Peak Viewers</th>
                      <th>Airtime</th>
                      <th>Scheduled / Created</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.recentStreams.map((s) => (
                      <tr key={s.id}>
                        <td>
                          <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{s.title}</div>
                          {s.description && (
                            <div style={{ fontSize: '12px', color: 'var(--text-muted)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '280px' }}>
                              {s.description}
                            </div>
                          )}
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
                          <strong style={{ color: s.peakViewers > 0 ? 'var(--accent-cyan)' : 'var(--text-muted)' }}>
                            {s.peakViewers || 0}
                          </strong>
                        </td>
                        <td style={{ color: 'var(--text-secondary)' }}>
                          {s.durationSeconds ? formatDuration(s.durationSeconds) : '—'}
                        </td>
                        <td style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                          {s.scheduledStartTime
                            ? new Date(s.scheduledStartTime).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' })
                            : new Date(s.createdAt).toLocaleDateString()}
                        </td>
                        <td>
                          <button
                            className="btn-ghost"
                            style={{ padding: '4px 10px', fontSize: '12px' }}
                            onClick={() => onOpenStream(s)}
                          >
                            Open →
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      ) : null}
    </div>
  )
}

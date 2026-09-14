import { useState } from 'react'

const CATEGORIES = [
  'Technology',
  'Gaming',
  'Creative',
  'Music',
  'Education',
  'Entertainment',
  'Sports',
]

export default function ScheduleStreamModal({
  isOpen,
  onClose,
  token,
  apiBaseUrl,
  onStreamScheduled,
  showToast,
}) {
  const [title, setTitle] = useState('')
  const [category, setCategory] = useState('Technology')
  const [description, setDescription] = useState('')
  const [isPublic, setIsPublic] = useState(true)

  // Default start time: tomorrow at 18:00
  const getDefaultStartTime = () => {
    const d = new Date()
    d.setDate(d.getDate() + 1)
    d.setHours(18, 0, 0, 0)
    return d.toISOString().slice(0, 16)
  }

  const [scheduledStartTime, setScheduledStartTime] = useState(getDefaultStartTime)
  const [scheduledEndTime, setScheduledEndTime] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  if (!isOpen) return null

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')

    if (!title.trim()) {
      setError('Title is required.')
      return
    }

    if (!scheduledStartTime) {
      setError('Scheduled start time is required.')
      return
    }

    const startDate = new Date(scheduledStartTime)
    if (startDate <= new Date()) {
      setError('Scheduled start time must be in the future.')
      return
    }

    if (scheduledEndTime) {
      const endDate = new Date(scheduledEndTime)
      if (endDate <= startDate) {
        setError('Scheduled end time must be after start time.')
        return
      }
    }

    setLoading(true)

    try {
      const payload = {
        title: title.trim(),
        category,
        description: description.trim(),
        isPublic,
        scheduledStartTime: scheduledStartTime + ':00',
        scheduledEndTime: scheduledEndTime ? scheduledEndTime + ':00' : null,
      }

      const res = await fetch(`${apiBaseUrl}/api/streams/schedule`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(payload),
      })

      const text = await res.text()
      if (!res.ok) {
        let msg = `Scheduling failed (${res.status})`
        try {
          const errObj = JSON.parse(text)
          if (errObj.message) msg = errObj.message
        } catch {
          // ignore
        }
        throw new Error(msg)
      }

      const created = JSON.parse(text)
      showToast('Live stream scheduled successfully!')
      onStreamScheduled(created)
      onClose()
    } catch (err) {
      setError(err.message || 'Failed to schedule stream.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-dialog" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span style={{ fontSize: '20px' }}>📅</span>
            <h3 className="modal-title">Schedule Live Broadcast</h3>
          </div>
          <button className="modal-close-btn" onClick={onClose}>✕</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="modal-body">
            {error && (
              <div className="error-banner" style={{ marginBottom: '16px' }}>
                <span>{error}</span>
              </div>
            )}

            <div className="form-group">
              <label className="form-label">Broadcast Title *</label>
              <input
                type="text"
                required
                className="form-input"
                placeholder="e.g. Special Product Keynote & QA"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
              />
            </div>

            <div className="form-row" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
              <div className="form-group">
                <label className="form-label">Category</label>
                <select
                  className="form-select"
                  value={category}
                  onChange={(e) => setCategory(e.target.value)}
                >
                  {CATEGORIES.map((c) => (
                    <option key={c} value={c}>{c}</option>
                  ))}
                </select>
              </div>

              <div className="form-group">
                <label className="form-label">Visibility</label>
                <select
                  className="form-select"
                  value={isPublic ? 'public' : 'private'}
                  onChange={(e) => setIsPublic(e.target.value === 'public')}
                >
                  <option value="public">Public (Discoverable)</option>
                  <option value="private">Private (Unlisted)</option>
                </select>
              </div>
            </div>

            <div className="form-row" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
              <div className="form-group">
                <label className="form-label">Scheduled Start Time *</label>
                <input
                  type="datetime-local"
                  required
                  className="form-input"
                  value={scheduledStartTime}
                  onChange={(e) => setScheduledStartTime(e.target.value)}
                />
              </div>

              <div className="form-group">
                <label className="form-label">Estimated End Time (Optional)</label>
                <input
                  type="datetime-local"
                  className="form-input"
                  value={scheduledEndTime}
                  onChange={(e) => setScheduledEndTime(e.target.value)}
                />
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Description</label>
              <textarea
                className="form-textarea"
                rows={3}
                placeholder="Share details about topics, agenda, or guest speakers..."
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </div>

            <div
              style={{
                padding: '12px 14px',
                borderRadius: 'var(--radius-sm)',
                background: 'rgba(99, 102, 241, 0.08)',
                border: '1px solid rgba(99, 102, 241, 0.2)',
                fontSize: '13px',
                color: 'var(--text-secondary)',
                lineHeight: '1.4',
              }}
            >
              💡 <strong>Stream Key Generated Instantly:</strong> You will receive your RTMP stream key right away to pre-configure OBS Studio or ffmpeg. The stream status will automatically turn <strong>LIVE</strong> when your RTMP feed is detected by SRS.
            </div>
          </div>

          <div className="modal-footer">
            <button
              type="button"
              className="btn-ghost"
              onClick={onClose}
              disabled={loading}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="btn-primary-gradient"
              disabled={loading}
            >
              {loading ? 'Scheduling...' : 'Confirm & Schedule Stream'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

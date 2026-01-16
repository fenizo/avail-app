"use client";

import React, { useState, useEffect } from 'react';
import { apiFetch } from '../../lib/api';

export default function SettingsPage() {
    const [syncInterval, setSyncInterval] = useState<string>("15");
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [message, setMessage] = useState<{ text: string, type: 'success' | 'error' } | null>(null);

    useEffect(() => {
        fetchSettings();
    }, []);

    const fetchSettings = async () => {
        try {
            const res = await apiFetch('/api/settings/sync-interval');
            const data = await res.json();
            setSyncInterval(data.value);
        } catch (error) {
            console.error("Failed to fetch settings", error);
        } finally {
            setLoading(false);
        }
    };

    const handleSave = async () => {
        setSaving(true);
        setMessage(null);
        try {
            const res = await apiFetch('/api/settings/sync-interval', {
                method: 'PATCH',
                body: JSON.stringify({ value: syncInterval })
            });

            if (res.ok) {
                setMessage({ text: "Settings saved successfully!", type: 'success' });
            } else {
                const err = await res.json();
                setMessage({ text: err.message || "Failed to save settings", type: 'error' });
            }
        } catch (error) {
            setMessage({ text: "An error occurred while saving", type: 'error' });
        } finally {
            setSaving(false);
        }
    };

    if (loading) {
        return <div style={{ padding: '40px', color: '#94a3b8' }}>Loading settings...</div>;
    }

    return (
        <div className="animate-enter" style={{ maxWidth: '800px' }}>
            <header style={{ marginBottom: '32px' }}>
                <h2 style={{ fontSize: '2rem', fontWeight: 700, marginBottom: '4px' }}>Settings</h2>
                <p style={{ color: '#94a3b8' }}>Configure global system preferences and mobile app behavior.</p>
            </header>

            <div className="glass-card" style={{ padding: '32px' }}>
                <div style={{ marginBottom: '40px' }}>
                    <h3 style={{ fontSize: '1.25rem', fontWeight: 600, marginBottom: '24px', color: 'var(--primary)' }}>Mobile Synchronization</h3>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', maxWidth: '500px' }}>
                            <div>
                                <label style={{ display: 'block', fontWeight: 500, marginBottom: '4px' }}>Sync Interval (Minutes)</label>
                                <p style={{ fontSize: '0.85rem', color: '#64748b' }}>How often mobile devices sync call logs to the server.</p>
                            </div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                <input
                                    type="number"
                                    min="15"
                                    max="1440"
                                    value={syncInterval}
                                    onChange={(e) => setSyncInterval(e.target.value)}
                                    style={{
                                        width: '80px',
                                        padding: '10px',
                                        background: 'rgba(255, 255, 255, 0.05)',
                                        border: '1px solid var(--card-border)',
                                        borderRadius: '8px',
                                        color: 'white',
                                        textAlign: 'center',
                                        fontSize: '1rem'
                                    }}
                                />
                                <span style={{ color: '#94a3b8', fontSize: '0.9rem' }}>m</span>
                            </div>
                        </div>

                        <div style={{
                            padding: '12px 16px',
                            background: 'rgba(56, 189, 248, 0.05)',
                            borderLeft: '4px solid var(--primary)',
                            borderRadius: '4px',
                            fontSize: '0.85rem',
                            color: '#e2e8f0',
                            maxWidth: '500px'
                        }}>
                            <strong>Note:</strong> Android OS allows a minimum interval of 15 minutes for periodic background tasks. Settings below this will default to 15.
                        </div>
                    </div>
                </div>

                <div style={{ borderTop: '1px solid var(--card-border)', paddingTop: '24px', display: 'flex', alignItems: 'center', gap: '24px' }}>
                    <button
                        onClick={handleSave}
                        disabled={saving}
                        className="btn-primary"
                        style={{ padding: '12px 32px' }}
                    >
                        {saving ? 'Saving Changes...' : 'Save Settings'}
                    </button>

                    {message && (
                        <span style={{
                            fontSize: '0.9rem',
                            color: message.type === 'success' ? '#22c55e' : '#ef4444',
                            fontWeight: 500
                        }}>
                            {message.text}
                        </span>
                    )}
                </div>
            </div>

            <div className="glass-card" style={{ padding: '32px', marginTop: '24px', opacity: 0.6 }}>
                <h3 style={{ fontSize: '1.25rem', fontWeight: 600, marginBottom: '16px', color: '#94a3b8' }}>Advanced Configuration</h3>
                <p style={{ color: '#64748b' }}>User management, API keys, and notification templates are currently managed via the database console.</p>
            </div>
        </div>
    );
}

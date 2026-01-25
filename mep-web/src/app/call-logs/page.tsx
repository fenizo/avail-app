"use client";

import React, { useEffect, useState } from 'react';
import { apiFetch } from '../../lib/api';

interface CallLog {
    id: string;
    staff: {
        id: string;
        name: string;
    };
    phoneNumber: string;
    contactName: string;
    callType: string;
    duration: string;
    timestamp: string;
}

const CallLogsPage = () => {
    const [rawLogs, setRawLogs] = useState<CallLog[]>([]);
    const [displayLogs, setDisplayLogs] = useState<CallLog[]>([]);
    const [loading, setLoading] = useState(true);
    const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
    const [filterMode, setFilterMode] = useState<'today' | 'yesterday' | 'custom' | 'all'>('all');
    const [customDate, setCustomDate] = useState<string>(new Date().toISOString().split('T')[0]);
    const [staffList, setStaffList] = useState<{ id: string, name: string }[]>([]);
    const [selectedStaff, setSelectedStaff] = useState<string>('all');
    const [expandedContact, setExpandedContact] = useState<string | null>(null);

    const fetchLogs = () => {
        setLoading(true);
        apiFetch('/api/call-logs')
            .then(res => res.json())
            .then(data => {
                if (Array.isArray(data)) {
                    setRawLogs(data);
                }
                setLoading(false);
                setLastUpdated(new Date());
            })
            .catch((err) => {
                setLoading(false);
                console.error("Error fetching logs:", err);
            });

        apiFetch('/api/staff')
            .then(res => res.json())
            .then(data => {
                if (Array.isArray(data)) {
                    setStaffList(data.map(s => ({ id: s.id, name: s.name })));
                }
            })
            .catch(err => console.error("Error fetching staff list"));
    };

    useEffect(() => {
        fetchLogs();

        // Auto-refresh every 5 seconds to show live sync progress
        const interval = setInterval(() => {
            fetchLogs();
        }, 5000);

        return () => clearInterval(interval);
    }, []);

    useEffect(() => {
        processLogs();
    }, [rawLogs, filterMode, customDate, selectedStaff]);

    const processLogs = () => {
        let filtered = [...rawLogs];

        // Date Filtering
        const today = new Date();
        today.setHours(0, 0, 0, 0);

        const yesterday = new Date();
        yesterday.setDate(yesterday.getDate() - 1);
        yesterday.setHours(0, 0, 0, 0);

        filtered = filtered.filter(log => {
            const logDate = new Date(log.timestamp);
            logDate.setHours(0, 0, 0, 0);

            if (filterMode === 'today') {
                return logDate.getTime() === today.getTime();
            } else if (filterMode === 'yesterday') {
                return logDate.getTime() === yesterday.getTime();
            } else if (filterMode === 'custom') {
                const targetDate = new Date(customDate);
                targetDate.setHours(0, 0, 0, 0);
                return logDate.getTime() === targetDate.getTime();
            }
            return true;
        }).filter(log => {
            if (selectedStaff === 'all') return true;
            return (log as any).staff?.id === selectedStaff;
        });

        // Sort and deduplicate by phone number (keep latest)
        filtered.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());

        const deduplicated: CallLog[] = [];
        const seenNumbers = new Set<string>();

        filtered.forEach(log => {
            if (!seenNumbers.has(log.phoneNumber)) {
                deduplicated.push(log);
                seenNumbers.add(log.phoneNumber);
            }
        });

        setDisplayLogs(deduplicated);
    };

    // Get all logs for a specific phone number
    const getContactLogs = (phoneNumber: string): CallLog[] => {
        return rawLogs
            .filter(log => log.phoneNumber === phoneNumber)
            .sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
    };

    return (
        <div className="animate-enter">
            <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '32px' }}>
                <div>
                    <h2 style={{ fontSize: '2rem', fontWeight: 700, marginBottom: '4px' }}>Call Logs</h2>
                    <p style={{ color: '#64748b' }}>Monitor and filter field staff communication.</p>
                </div>
                <div style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
                    {lastUpdated && <span style={{ fontSize: '0.8rem', color: '#374151' }}>Last updated: {lastUpdated.toLocaleTimeString()}</span>}
                    <button onClick={fetchLogs} disabled={loading} className="btn-primary">
                        {loading ? 'Updating...' : 'Refresh Logs'}
                    </button>
                </div>
            </header>

            {/* Stats Cards - Contact Count & Monthly Breakdown */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: '16px', marginBottom: '24px' }}>
                {/* Sync Status Card */}
                <div className="glass-card" style={{ padding: '20px', background: 'linear-gradient(135deg, rgba(56, 189, 248, 0.1), rgba(14, 165, 233, 0.05))' }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '12px' }}>
                        <div style={{ fontSize: '0.875rem', fontWeight: 500, color: '#38bdf8' }}>Sync Status</div>
                        {loading && (
                            <div style={{
                                width: '12px',
                                height: '12px',
                                borderRadius: '50%',
                                background: '#38bdf8',
                                animation: 'pulse 1.5s cubic-bezier(0.4, 0, 0.6, 1) infinite'
                            }}></div>
                        )}
                    </div>
                    {lastUpdated ? (
                        <>
                            <div style={{ fontSize: '0.875rem', color: '#64748b', marginBottom: '4px' }}>Last Synced</div>
                            <div style={{ fontSize: '1.1rem', fontWeight: 600, color: '#1a1a1a' }}>
                                {lastUpdated.toLocaleTimeString()}
                            </div>
                            <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '8px' }}>
                                {loading ? '🔄 Checking for new logs...' : '✅ Up to date'}
                            </div>
                        </>
                    ) : (
                        <div style={{ fontSize: '0.875rem', color: '#64748b' }}>Connecting...</div>
                    )}
                </div>

                {/* Total Contacts Card */}
                <div className="glass-card" style={{ padding: '20px', textAlign: 'center' }}>
                    <div style={{ fontSize: '0.875rem', color: '#64748b', marginBottom: '8px' }}>Total Unique Contacts</div>
                    <div style={{ fontSize: '2.5rem', fontWeight: 700, color: '#38bdf8' }}>{displayLogs.length}</div>
                    <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '4px' }}>
                        {filterMode === 'all' ? 'All time' : filterMode === 'today' ? 'Today' : filterMode === 'yesterday' ? 'Yesterday' : 'Custom date'}
                    </div>
                </div>

                {/* Monthly Breakdown */}
                {filterMode === 'all' && (() => {
                    // Group logs by month
                    const monthlyData: { [key: string]: number } = {};
                    rawLogs.forEach(log => {
                        const date = new Date(log.timestamp);
                        const monthKey = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
                        monthlyData[monthKey] = (monthlyData[monthKey] || 0) + 1;
                    });

                    // Get last 3 months
                    const sortedMonths = Object.keys(monthlyData).sort().reverse().slice(0, 3);

                    return sortedMonths.map(monthKey => {
                        const [year, month] = monthKey.split('-');
                        const monthName = new Date(parseInt(year), parseInt(month) - 1).toLocaleString('default', { month: 'short', year: 'numeric' });

                        return (
                            <div key={monthKey} className="glass-card" style={{ padding: '20px', textAlign: 'center' }}>
                                <div style={{ fontSize: '0.875rem', color: '#64748b', marginBottom: '8px' }}>{monthName}</div>
                                <div style={{ fontSize: '2rem', fontWeight: 700, color: '#10b981' }}>{monthlyData[monthKey]}</div>
                                <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '4px' }}>calls logged</div>
                            </div>
                        );
                    });
                })()}
            </div>


            {/* Filter Bar */}
            <div className="glass-card" style={{ padding: '16px', marginBottom: '24px', display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
                <span style={{ fontSize: '0.9rem', color: '#64748b', marginRight: '8px' }}>Filters:</span>

                <button
                    onClick={() => setFilterMode('all')}
                    className={`btn-filter ${filterMode === 'all' ? 'active' : ''}`}
                    style={filterStyle(filterMode === 'all')}
                >
                    All Logs
                </button>

                <button
                    onClick={() => setFilterMode('today')}
                    className={`btn-filter ${filterMode === 'today' ? 'active' : ''}`}
                    style={filterStyle(filterMode === 'today')}
                >
                    Today
                </button>

                <button
                    onClick={() => setFilterMode('yesterday')}
                    className={`btn-filter ${filterMode === 'yesterday' ? 'active' : ''}`}
                    style={filterStyle(filterMode === 'yesterday')}
                >
                    Yesterday
                </button>

                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginLeft: '12px' }}>
                    <span style={{ fontSize: '0.85rem', color: '#64748b' }}>Staff:</span>
                    <select
                        value={selectedStaff}
                        onChange={(e) => setSelectedStaff(e.target.value)}
                        style={{
                            background: 'rgba(255, 255, 255, 0.05)',
                            border: '1px solid var(--card-border)',
                            borderRadius: '6px',
                            color: 'white',
                            padding: '6px 10px',
                            fontSize: '0.85rem',
                            outline: 'none'
                        }}
                    >
                        <option value="all">All Staff</option>
                        {staffList.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
                    </select>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginLeft: 'auto' }}>
                    <span style={{ fontSize: '0.85rem', color: '#64748b' }}>Custom Date:</span>
                    <input
                        type="date"
                        value={customDate}
                        onChange={(e) => {
                            setCustomDate(e.target.value);
                            setFilterMode('custom');
                        }}
                        style={{
                            background: 'rgba(255, 255, 255, 0.05)',
                            border: '1px solid var(--card-border)',
                            borderRadius: '6px',
                            color: 'white',
                            padding: '6px 10px',
                            fontSize: '0.85rem',
                            outline: 'none'
                        }}
                    />
                </div>
            </div>

            {/* Contact Cards */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                {loading && displayLogs.length === 0 ? (
                    <div className="glass-card" style={{ padding: '48px', textAlign: 'center', color: '#374151' }}>
                        Fetching call history...
                    </div>
                ) : displayLogs.length === 0 ? (
                    <div className="glass-card" style={{ padding: '48px', textAlign: 'center', color: '#374151' }}>
                        No call logs found for this filter.
                    </div>
                ) : (
                    displayLogs.map((log) => {
                        const contactLogs = getContactLogs(log.phoneNumber);
                        const isExpanded = expandedContact === log.phoneNumber;
                        const totalCalls = contactLogs.length;

                        return (
                            <div key={log.phoneNumber} className="glass-card" style={{ overflow: 'hidden' }}>
                                {/* Contact Header - Clickable */}
                                <div
                                    onClick={() => setExpandedContact(isExpanded ? null : log.phoneNumber)}
                                    style={{
                                        padding: '20px 24px',
                                        cursor: 'pointer',
                                        transition: 'background 0.2s',
                                        background: isExpanded ? 'rgba(56, 189, 248, 0.05)' : 'transparent',
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        alignItems: 'center'
                                    }}
                                    onMouseEnter={(e) => e.currentTarget.style.background = 'rgba(255, 255, 255, 0.02)'}
                                    onMouseLeave={(e) => e.currentTarget.style.background = isExpanded ? 'rgba(56, 189, 248, 0.05)' : 'transparent'}
                                >
                                    <div style={{ display: 'flex', gap: '20px', alignItems: 'center', flex: 1 }}>
                                        {/* Contact Name/Number */}
                                        <div style={{ flex: 1 }}>
                                            <div style={{ fontSize: '1.1rem', fontWeight: 600, color: '#1a1a1a', marginBottom: '4px' }}>
                                                {log.contactName || log.phoneNumber}
                                            </div>
                                            {log.contactName && (
                                                <div style={{ fontSize: '0.875rem', color: '#38bdf8' }}>{log.phoneNumber}</div>
                                            )}
                                        </div>

                                        {/* Stats */}
                                        <div style={{ display: 'flex', gap: '24px', alignItems: 'center' }}>
                                            <div style={{ textAlign: 'center' }}>
                                                <div style={{ fontSize: '0.75rem', color: '#64748b', marginBottom: '2px' }}>Total Calls</div>
                                                <div style={{ fontSize: '1.25rem', fontWeight: 700, color: '#38bdf8' }}>{totalCalls}</div>
                                            </div>

                                            <div style={{ textAlign: 'center' }}>
                                                <div style={{ fontSize: '0.75rem', color: '#64748b', marginBottom: '2px' }}>Last Call</div>
                                                <div style={{ fontSize: '0.875rem', color: '#64748b' }}>
                                                    {new Date(log.timestamp).toLocaleDateString()}
                                                </div>
                                            </div>

                                            <div style={{ textAlign: 'center' }}>
                                                <div style={{ fontSize: '0.75rem', color: '#64748b', marginBottom: '2px' }}>Staff</div>
                                                <div style={{ fontSize: '0.875rem', fontWeight: 500 }}>{log.staff?.name || 'Admin'}</div>
                                            </div>
                                        </div>

                                        {/* Expand Icon */}
                                        <div style={{ fontSize: '1.5rem', color: '#38bdf8', transition: 'transform 0.2s', transform: isExpanded ? 'rotate(180deg)' : 'rotate(0deg)' }}>
                                            ▼
                                        </div>
                                    </div>
                                </div>

                                {/* Expanded Call History */}
                                {isExpanded && (
                                    <div style={{ borderTop: '1px solid var(--card-border)', padding: '16px 24px', background: 'rgba(0, 0, 0, 0.2)' }}>
                                        <div style={{ fontSize: '0.875rem', color: '#64748b', marginBottom: '16px', fontWeight: 500 }}>
                                            Call History ({totalCalls} calls)
                                        </div>
                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                                            {contactLogs.map((callLog, index) => (
                                                <div
                                                    key={callLog.id}
                                                    style={{
                                                        padding: '12px 16px',
                                                        background: 'rgba(255, 255, 255, 0.02)',
                                                        borderRadius: '8px',
                                                        display: 'flex',
                                                        justifyContent: 'space-between',
                                                        alignItems: 'center',
                                                        borderLeft: `3px solid ${callLog.callType === 'OUTGOING' ? '#38bdf8' : callLog.callType === 'INCOMING' ? '#22c55e' : '#ef4444'}`
                                                    }}
                                                >
                                                    <div style={{ display: 'flex', gap: '16px', alignItems: 'center', flex: 1 }}>
                                                        <span style={{
                                                            padding: '4px 10px',
                                                            borderRadius: '6px',
                                                            fontSize: '0.75rem',
                                                            fontWeight: 600,
                                                            background: callLog.callType === 'OUTGOING' ? 'rgba(56, 189, 248, 0.15)' : callLog.callType === 'INCOMING' ? 'rgba(34, 197, 94, 0.15)' : 'rgba(239, 68, 68, 0.15)',
                                                            color: callLog.callType === 'OUTGOING' ? '#38bdf8' : callLog.callType === 'INCOMING' ? '#22c55e' : '#ef4444'
                                                        }}>
                                                            {callLog.callType}
                                                        </span>

                                                        <div style={{ fontSize: '0.875rem', color: '#64748b' }}>
                                                            {new Date(callLog.timestamp).toLocaleString()}
                                                        </div>

                                                        <div style={{ fontSize: '0.875rem', color: '#64748b' }}>
                                                            Duration: {callLog.duration}s
                                                        </div>
                                                    </div>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}
                            </div>
                        );
                    })
                )}
            </div>
        </div>
    );
};

// Simple helper for button styles
const filterStyle = (isActive: boolean): React.CSSProperties => ({
    padding: '8px 16px',
    borderRadius: '8px',
    fontSize: '0.85rem',
    cursor: 'pointer',
    border: isActive ? '1px solid var(--primary)' : '1px solid var(--card-border)',
    background: isActive ? 'rgba(56, 189, 248, 0.1)' : 'transparent',
    color: isActive ? 'var(--primary)' : '#64748b',
    transition: 'all 0.2s'
});

export default CallLogsPage;

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
    }, []);

    useEffect(() => {
        processLogs();
    }, [rawLogs, filterMode, customDate, selectedStaff]);

    const processLogs = () => {
        let filtered = [...rawLogs];

        // 1. Date Filtering
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
            // The log.staff.id is where we want to filter, but let's check log structure.
            // backend CallLog has @ManyToOne staff: User.
            // So log.staff should exist.
            return (log as any).staff?.id === selectedStaff;
        });

        // 2. Deduplication (Group by phone number, keep latest)
        // We sort by timestamp descending first to ensure the first one we find is the latest
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

    return (
        <div className="animate-enter">
            <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '32px' }}>
                <div>
                    <h2 style={{ fontSize: '2rem', fontWeight: 700, marginBottom: '4px' }}>Call Logs</h2>
                    <p style={{ color: '#94a3b8' }}>Monitor and filter field staff communication.</p>
                </div>
                <div style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
                    {lastUpdated && <span style={{ fontSize: '0.8rem', color: '#475569' }}>Last updated: {lastUpdated.toLocaleTimeString()}</span>}
                    <button onClick={fetchLogs} disabled={loading} className="btn-primary">
                        {loading ? 'Updating...' : 'Refresh Logs'}
                    </button>
                </div>
            </header>

            {/* Filter Bar */}
            <div className="glass-card" style={{ padding: '16px', marginBottom: '24px', display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
                <span style={{ fontSize: '0.9rem', color: '#94a3b8', marginRight: '8px' }}>Filters:</span>

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
                    <span style={{ fontSize: '0.85rem', color: '#94a3b8' }}>Staff:</span>
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
                    <span style={{ fontSize: '0.85rem', color: '#94a3b8' }}>Custom Date:</span>
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

            <div className="glass-card" style={{ overflow: 'hidden' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                    <thead>
                        <tr style={{ background: 'rgba(255, 255, 255, 0.02)', borderBottom: '1px solid var(--card-border)' }}>
                            <th style={{ padding: '16px 24px', color: '#94a3b8', fontSize: '0.875rem', fontWeight: 500 }}>Staff</th>
                            <th style={{ padding: '16px 24px', color: '#94a3b8', fontSize: '0.875rem', fontWeight: 500 }}>Contact Name</th>
                            <th style={{ padding: '16px 24px', color: '#94a3b8', fontSize: '0.875rem', fontWeight: 500 }}>Phone Number</th>
                            <th style={{ padding: '16px 24px', color: '#94a3b8', fontSize: '0.875rem', fontWeight: 500 }}>Type</th>
                            <th style={{ padding: '16px 24px', color: '#94a3b8', fontSize: '0.875rem', fontWeight: 500 }}>Last Duration (s)</th>
                            <th style={{ padding: '16px 24px', color: '#94a3b8', fontSize: '0.875rem', fontWeight: 500 }}>Last Call Time</th>
                        </tr>
                    </thead>
                    <tbody>
                        {loading && displayLogs.length === 0 ? (
                            <tr><td colSpan={5} style={{ padding: '48px', textAlign: 'center', color: '#475569' }}>Fetching call history...</td></tr>
                        ) : displayLogs.length === 0 ? (
                            <tr><td colSpan={5} style={{ padding: '48px', textAlign: 'center', color: '#475569' }}>No call logs found for this filter.</td></tr>
                        ) : (
                            displayLogs.map((log) => (
                                <tr key={log.id} style={{ borderBottom: '1px solid var(--card-border)', transition: 'background 0.2s' }}>
                                    <td style={{ padding: '16px 24px', fontWeight: 500 }}>{log.staff?.name || 'Admin'}</td>
                                    <td style={{ padding: '16px 24px', fontWeight: 500, color: '#f8fafc' }}>{log.contactName || '-'}</td>
                                    <td style={{ padding: '16px 24px', color: '#38bdf8' }}>{log.phoneNumber}</td>
                                    <td style={{ padding: '16px 24px' }}>
                                        <span style={{
                                            padding: '4px 8px',
                                            borderRadius: '4px',
                                            fontSize: '0.7rem',
                                            background: log.callType === 'OUTGOING' ? 'rgba(56, 189, 248, 0.1)' : 'rgba(34, 197, 94, 0.1)',
                                            color: log.callType === 'OUTGOING' ? '#38bdf8' : '#22c55e'
                                        }}>
                                            {log.callType}
                                        </span>
                                    </td>
                                    <td style={{ padding: '16px 24px', color: '#94a3b8' }}>{log.duration}</td>
                                    <td style={{ padding: '16px 24px', color: '#94a3b8', fontSize: '0.875rem' }}>
                                        {new Date(log.timestamp).toLocaleString()}
                                    </td>
                                </tr>
                            ))
                        )}
                    </tbody>
                </table>
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
    color: isActive ? 'var(--primary)' : '#94a3b8',
    transition: 'all 0.2s'
});

export default CallLogsPage;

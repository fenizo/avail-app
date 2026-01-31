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

const EXCLUDED_CONTACTS_KEY = 'excludedContacts';

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
    const [excludedContacts, setExcludedContacts] = useState<Set<string>>(new Set());
    const [showExcludeModal, setShowExcludeModal] = useState(false);
    const [excludeInput, setExcludeInput] = useState('');

    // Load excluded contacts from localStorage
    useEffect(() => {
        const saved = localStorage.getItem(EXCLUDED_CONTACTS_KEY);
        if (saved) {
            setExcludedContacts(new Set(JSON.parse(saved)));
        }
    }, []);

    // Save excluded contacts to localStorage
    const saveExcludedContacts = (contacts: Set<string>) => {
        localStorage.setItem(EXCLUDED_CONTACTS_KEY, JSON.stringify(Array.from(contacts)));
        setExcludedContacts(contacts);
    };

    const addExcludedContact = (phone: string) => {
        const newSet = new Set(excludedContacts);
        newSet.add(phone.trim());
        saveExcludedContacts(newSet);
    };

    const removeExcludedContact = (phone: string) => {
        const newSet = new Set(excludedContacts);
        newSet.delete(phone);
        saveExcludedContacts(newSet);
    };

    // August 1, 2025 cutoff date
    const AUG_2025_CUTOFF = new Date(2025, 7, 1, 0, 0, 0, 0).getTime();

    const fetchLogs = () => {
        setLoading(true);
        apiFetch('/api/call-logs')
            .then(res => res.json())
            .then(data => {
                if (Array.isArray(data)) {
                    const filteredData = data.filter((log: CallLog) => {
                        const logTime = new Date(log.timestamp).getTime();
                        return logTime >= AUG_2025_CUTOFF;
                    });
                    setRawLogs(filteredData);
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
        const interval = setInterval(() => {
            fetchLogs();
        }, 5000);
        return () => clearInterval(interval);
    }, []);

    useEffect(() => {
        processLogs();
    }, [rawLogs, filterMode, customDate, selectedStaff, excludedContacts]);

    const processLogs = () => {
        let filtered = [...rawLogs];

        const today = new Date();
        today.setHours(0, 0, 0, 0);

        const yesterday = new Date();
        yesterday.setDate(yesterday.getDate() - 1);
        yesterday.setHours(0, 0, 0, 0);

        filtered = filtered.filter(log => {
            // Exclude filtered contacts
            if (excludedContacts.has(log.phoneNumber)) return false;

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

    const getContactLogs = (phoneNumber: string): CallLog[] => {
        return rawLogs
            .filter(log => log.phoneNumber === phoneNumber)
            .sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
    };

    const formatDuration = (duration: string) => {
        const secs = parseInt(duration);
        if (secs >= 60) {
            return `${Math.floor(secs / 60)}m ${secs % 60}s`;
        }
        return `${secs}s`;
    };

    return (
        <div className="animate-enter" style={{ padding: '0 8px' }}>
            {/* Header - Mobile Responsive */}
            <header style={{
                display: 'flex',
                flexDirection: 'column',
                gap: '16px',
                marginBottom: '24px'
            }}>
                <div>
                    <h2 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '4px' }}>Call Logs</h2>
                    <p style={{ color: '#64748b', fontSize: '0.875rem' }}>Monitor field staff communication</p>
                </div>
                <div style={{
                    display: 'flex',
                    gap: '8px',
                    alignItems: 'center',
                    flexWrap: 'wrap'
                }}>
                    {lastUpdated && (
                        <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
                            Updated: {lastUpdated.toLocaleTimeString()}
                        </span>
                    )}
                    <button
                        onClick={() => setShowExcludeModal(true)}
                        style={{
                            background: 'rgba(239, 68, 68, 0.1)',
                            border: '1px solid #ef4444',
                            color: '#ef4444',
                            padding: '8px 12px',
                            borderRadius: '8px',
                            cursor: 'pointer',
                            fontWeight: 500,
                            fontSize: '0.8rem'
                        }}
                    >
                        Exclude ({excludedContacts.size})
                    </button>
                    <button
                        onClick={() => {
                            window.open('https://staging.maduraielectriciansandplumbers.com/api/call-logs/export/excel', '_blank');
                        }}
                        style={{
                            background: 'linear-gradient(135deg, #10b981, #059669)',
                            border: 'none',
                            color: 'white',
                            padding: '8px 12px',
                            borderRadius: '8px',
                            cursor: 'pointer',
                            fontWeight: 500,
                            fontSize: '0.8rem'
                        }}
                    >
                        Excel
                    </button>
                    <button onClick={fetchLogs} disabled={loading} className="btn-primary" style={{ padding: '8px 12px', fontSize: '0.8rem' }}>
                        {loading ? '...' : 'Refresh'}
                    </button>
                </div>
            </header>

            {/* Exclude Modal */}
            {showExcludeModal && (
                <div style={{
                    position: 'fixed',
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    background: 'rgba(0,0,0,0.7)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    zIndex: 1000,
                    padding: '16px'
                }}>
                    <div style={{
                        background: '#1e293b',
                        borderRadius: '16px',
                        padding: '24px',
                        width: '100%',
                        maxWidth: '400px',
                        maxHeight: '80vh',
                        overflow: 'auto'
                    }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                            <h3 style={{ fontSize: '1.2rem', fontWeight: 600, color: 'white' }}>Excluded Contacts</h3>
                            <button
                                onClick={() => setShowExcludeModal(false)}
                                style={{ background: 'none', border: 'none', color: '#64748b', fontSize: '1.5rem', cursor: 'pointer' }}
                            >
                                ×
                            </button>
                        </div>

                        {/* Add new exclusion */}
                        <div style={{ display: 'flex', gap: '8px', marginBottom: '16px' }}>
                            <input
                                type="text"
                                placeholder="Phone number to exclude"
                                value={excludeInput}
                                onChange={(e) => setExcludeInput(e.target.value)}
                                style={{
                                    flex: 1,
                                    padding: '10px',
                                    borderRadius: '8px',
                                    border: '1px solid #374151',
                                    background: '#0f172a',
                                    color: 'white',
                                    fontSize: '0.9rem'
                                }}
                            />
                            <button
                                onClick={() => {
                                    if (excludeInput.trim()) {
                                        addExcludedContact(excludeInput);
                                        setExcludeInput('');
                                    }
                                }}
                                style={{
                                    background: '#ef4444',
                                    border: 'none',
                                    color: 'white',
                                    padding: '10px 16px',
                                    borderRadius: '8px',
                                    cursor: 'pointer',
                                    fontWeight: 500
                                }}
                            >
                                Add
                            </button>
                        </div>

                        {/* List of excluded contacts */}
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                            {Array.from(excludedContacts).length === 0 ? (
                                <p style={{ color: '#64748b', textAlign: 'center', padding: '16px' }}>
                                    No excluded contacts
                                </p>
                            ) : (
                                Array.from(excludedContacts).map(phone => (
                                    <div
                                        key={phone}
                                        style={{
                                            display: 'flex',
                                            justifyContent: 'space-between',
                                            alignItems: 'center',
                                            padding: '10px 12px',
                                            background: 'rgba(239, 68, 68, 0.1)',
                                            borderRadius: '8px',
                                            border: '1px solid rgba(239, 68, 68, 0.3)'
                                        }}
                                    >
                                        <span style={{ color: 'white' }}>{phone}</span>
                                        <button
                                            onClick={() => removeExcludedContact(phone)}
                                            style={{
                                                background: 'none',
                                                border: 'none',
                                                color: '#22c55e',
                                                cursor: 'pointer',
                                                fontSize: '0.85rem',
                                                fontWeight: 500
                                            }}
                                        >
                                            Restore
                                        </button>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                </div>
            )}

            {/* Stats Cards - Mobile Grid */}
            <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(2, 1fr)',
                gap: '12px',
                marginBottom: '20px'
            }}>
                {/* Today's Contacts */}
                {(() => {
                    const today = new Date();
                    today.setHours(0, 0, 0, 0);
                    const todayContacts = new Set(
                        rawLogs.filter(log => {
                            if (excludedContacts.has(log.phoneNumber)) return false;
                            const logDate = new Date(log.timestamp);
                            logDate.setHours(0, 0, 0, 0);
                            return logDate.getTime() === today.getTime();
                        }).map(log => log.phoneNumber)
                    );
                    return (
                        <div className="glass-card" style={{ padding: '16px', textAlign: 'center' }}>
                            <div style={{ fontSize: '0.75rem', color: '#22c55e', marginBottom: '4px', fontWeight: 500 }}>Today</div>
                            <div style={{ fontSize: '2rem', fontWeight: 700, color: '#22c55e' }}>{todayContacts.size}</div>
                        </div>
                    );
                })()}

                {/* Yesterday's Contacts */}
                {(() => {
                    const yesterday = new Date();
                    yesterday.setDate(yesterday.getDate() - 1);
                    yesterday.setHours(0, 0, 0, 0);
                    const yesterdayContacts = new Set(
                        rawLogs.filter(log => {
                            if (excludedContacts.has(log.phoneNumber)) return false;
                            const logDate = new Date(log.timestamp);
                            logDate.setHours(0, 0, 0, 0);
                            return logDate.getTime() === yesterday.getTime();
                        }).map(log => log.phoneNumber)
                    );
                    return (
                        <div className="glass-card" style={{ padding: '16px', textAlign: 'center' }}>
                            <div style={{ fontSize: '0.75rem', color: '#f97316', marginBottom: '4px', fontWeight: 500 }}>Yesterday</div>
                            <div style={{ fontSize: '2rem', fontWeight: 700, color: '#f97316' }}>{yesterdayContacts.size}</div>
                        </div>
                    );
                })()}

                {/* Total Contacts */}
                <div className="glass-card" style={{ padding: '16px', textAlign: 'center' }}>
                    <div style={{ fontSize: '0.75rem', color: '#38bdf8', marginBottom: '4px', fontWeight: 500 }}>Total</div>
                    <div style={{ fontSize: '2rem', fontWeight: 700, color: '#38bdf8' }}>{displayLogs.length}</div>
                </div>

                {/* Sync Status */}
                <div className="glass-card" style={{ padding: '16px', textAlign: 'center' }}>
                    <div style={{ fontSize: '0.75rem', color: '#64748b', marginBottom: '4px' }}>Status</div>
                    <div style={{ fontSize: '0.9rem', fontWeight: 600, color: loading ? '#38bdf8' : '#22c55e' }}>
                        {loading ? 'Syncing...' : 'Live'}
                    </div>
                </div>
            </div>

            {/* Filter Bar - Mobile Scrollable */}
            <div className="glass-card" style={{
                padding: '12px',
                marginBottom: '16px',
                overflowX: 'auto',
                WebkitOverflowScrolling: 'touch'
            }}>
                <div style={{
                    display: 'flex',
                    gap: '8px',
                    alignItems: 'center',
                    minWidth: 'max-content'
                }}>
                    <button
                        onClick={() => setFilterMode('all')}
                        style={filterStyle(filterMode === 'all')}
                    >
                        All
                    </button>
                    <button
                        onClick={() => setFilterMode('today')}
                        style={filterStyle(filterMode === 'today')}
                    >
                        Today
                    </button>
                    <button
                        onClick={() => setFilterMode('yesterday')}
                        style={filterStyle(filterMode === 'yesterday')}
                    >
                        Yesterday
                    </button>

                    <select
                        value={selectedStaff}
                        onChange={(e) => setSelectedStaff(e.target.value)}
                        style={{
                            background: 'rgba(255, 255, 255, 0.05)',
                            border: '1px solid var(--card-border)',
                            borderRadius: '6px',
                            color: 'white',
                            padding: '6px 8px',
                            fontSize: '0.8rem',
                            outline: 'none'
                        }}
                    >
                        <option value="all">All Staff</option>
                        {staffList.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
                    </select>

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
                            padding: '6px 8px',
                            fontSize: '0.8rem',
                            outline: 'none'
                        }}
                    />
                </div>
            </div>

            {/* Contact Cards - Mobile Optimized */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {loading && displayLogs.length === 0 ? (
                    <div className="glass-card" style={{ padding: '32px', textAlign: 'center', color: '#64748b' }}>
                        Loading...
                    </div>
                ) : displayLogs.length === 0 ? (
                    <div className="glass-card" style={{ padding: '32px', textAlign: 'center', color: '#64748b' }}>
                        No call logs found
                    </div>
                ) : (
                    displayLogs.map((log) => {
                        const contactLogs = getContactLogs(log.phoneNumber);
                        const isExpanded = expandedContact === log.phoneNumber;
                        const totalCalls = contactLogs.length;

                        return (
                            <div key={log.phoneNumber} className="glass-card" style={{ overflow: 'hidden' }}>
                                {/* Contact Header */}
                                <div
                                    onClick={() => setExpandedContact(isExpanded ? null : log.phoneNumber)}
                                    style={{
                                        padding: '14px 16px',
                                        cursor: 'pointer',
                                        background: isExpanded ? 'rgba(56, 189, 248, 0.05)' : 'transparent'
                                    }}
                                >
                                    {/* Top Row: Name + Exclude Button */}
                                    <div style={{
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        alignItems: 'flex-start',
                                        marginBottom: '8px'
                                    }}>
                                        <div style={{ flex: 1 }}>
                                            <div style={{
                                                fontSize: '1rem',
                                                fontWeight: 600,
                                                color: '#1a1a1a',
                                                marginBottom: '2px'
                                            }}>
                                                {log.contactName || log.phoneNumber}
                                            </div>
                                            {log.contactName && (
                                                <div style={{ fontSize: '0.8rem', color: '#38bdf8' }}>
                                                    {log.phoneNumber}
                                                </div>
                                            )}
                                        </div>
                                        <button
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                addExcludedContact(log.phoneNumber);
                                            }}
                                            style={{
                                                background: 'rgba(239, 68, 68, 0.1)',
                                                border: 'none',
                                                color: '#ef4444',
                                                padding: '4px 8px',
                                                borderRadius: '4px',
                                                cursor: 'pointer',
                                                fontSize: '0.7rem',
                                                fontWeight: 500
                                            }}
                                        >
                                            Exclude
                                        </button>
                                    </div>

                                    {/* Stats Row - Mobile Optimized */}
                                    <div style={{
                                        display: 'grid',
                                        gridTemplateColumns: 'repeat(4, 1fr)',
                                        gap: '8px',
                                        marginBottom: '8px'
                                    }}>
                                        <div style={{ textAlign: 'center' }}>
                                            <div style={{ fontSize: '0.65rem', color: '#64748b' }}>Calls</div>
                                            <div style={{ fontSize: '1rem', fontWeight: 700, color: '#38bdf8' }}>{totalCalls}</div>
                                        </div>
                                        <div style={{ textAlign: 'center' }}>
                                            <div style={{ fontSize: '0.65rem', color: '#64748b' }}>Last</div>
                                            <div style={{ fontSize: '0.75rem', fontWeight: 500, color: '#1a1a1a' }}>
                                                {new Date(log.timestamp).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
                                            </div>
                                        </div>
                                        <div style={{ textAlign: 'center' }}>
                                            <div style={{ fontSize: '0.65rem', color: '#64748b' }}>Time</div>
                                            <div style={{ fontSize: '0.75rem', fontWeight: 500, color: '#38bdf8' }}>
                                                {new Date(log.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                                            </div>
                                        </div>
                                        <div style={{ textAlign: 'center' }}>
                                            <div style={{ fontSize: '0.65rem', color: '#64748b' }}>Duration</div>
                                            <div style={{ fontSize: '0.75rem', fontWeight: 500, color: '#10b981' }}>
                                                {formatDuration(log.duration)}
                                            </div>
                                        </div>
                                    </div>

                                    {/* Staff + Expand */}
                                    <div style={{
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        alignItems: 'center'
                                    }}>
                                        <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
                                            Staff: <span style={{ color: '#1a1a1a', fontWeight: 500 }}>{log.staff?.name || 'Admin'}</span>
                                        </span>
                                        <span style={{
                                            fontSize: '0.8rem',
                                            color: '#38bdf8',
                                            transform: isExpanded ? 'rotate(180deg)' : 'rotate(0deg)',
                                            transition: 'transform 0.2s'
                                        }}>
                                            ▼
                                        </span>
                                    </div>
                                </div>

                                {/* Expanded Call History */}
                                {isExpanded && (
                                    <div style={{
                                        borderTop: '1px solid var(--card-border)',
                                        padding: '12px 16px',
                                        background: 'rgba(0, 0, 0, 0.2)'
                                    }}>
                                        <div style={{
                                            fontSize: '0.8rem',
                                            color: '#64748b',
                                            marginBottom: '12px'
                                        }}>
                                            History ({totalCalls} calls)
                                        </div>
                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                            {contactLogs.map((callLog) => (
                                                <div
                                                    key={callLog.id}
                                                    style={{
                                                        padding: '10px 12px',
                                                        background: 'rgba(255, 255, 255, 0.02)',
                                                        borderRadius: '6px',
                                                        borderLeft: `3px solid ${
                                                            callLog.callType === 'OUTGOING' ? '#38bdf8' :
                                                            callLog.callType === 'INCOMING' ? '#22c55e' : '#ef4444'
                                                        }`
                                                    }}
                                                >
                                                    <div style={{
                                                        display: 'flex',
                                                        justifyContent: 'space-between',
                                                        alignItems: 'center',
                                                        flexWrap: 'wrap',
                                                        gap: '8px'
                                                    }}>
                                                        <span style={{
                                                            padding: '2px 8px',
                                                            borderRadius: '4px',
                                                            fontSize: '0.7rem',
                                                            fontWeight: 600,
                                                            background: callLog.callType === 'OUTGOING'
                                                                ? 'rgba(56, 189, 248, 0.15)'
                                                                : callLog.callType === 'INCOMING'
                                                                    ? 'rgba(34, 197, 94, 0.15)'
                                                                    : 'rgba(239, 68, 68, 0.15)',
                                                            color: callLog.callType === 'OUTGOING'
                                                                ? '#38bdf8'
                                                                : callLog.callType === 'INCOMING'
                                                                    ? '#22c55e'
                                                                    : '#ef4444'
                                                        }}>
                                                            {callLog.callType}
                                                        </span>
                                                        <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
                                                            {new Date(callLog.timestamp).toLocaleString()}
                                                        </span>
                                                        <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
                                                            {formatDuration(callLog.duration)}
                                                        </span>
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

const filterStyle = (isActive: boolean): React.CSSProperties => ({
    padding: '6px 12px',
    borderRadius: '6px',
    fontSize: '0.8rem',
    cursor: 'pointer',
    border: isActive ? '1px solid var(--primary)' : '1px solid var(--card-border)',
    background: isActive ? 'rgba(56, 189, 248, 0.1)' : 'transparent',
    color: isActive ? 'var(--primary)' : '#64748b',
    transition: 'all 0.2s',
    whiteSpace: 'nowrap'
});

export default CallLogsPage;

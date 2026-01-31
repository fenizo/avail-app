"use client";

import React, { useEffect, useState } from 'react';
import { apiFetch } from '../../lib/api';

const EXCLUDED_CONTACTS_KEY = 'excludedContacts';

// Normalize phone number - remove +91 or 91 prefix to get base 10-digit number
const normalizePhone = (phone: string): string => {
    if (!phone) return phone;
    let normalized = phone.replace(/\s+/g, '').replace(/-/g, '');
    if (normalized.startsWith('+91')) {
        normalized = normalized.substring(3);
    } else if (normalized.startsWith('91') && normalized.length > 10) {
        normalized = normalized.substring(2);
    }
    return normalized;
};

// Check if phone is in excluded set (handles +91 variants)
const isExcluded = (phone: string, excludedSet: Set<string>): boolean => {
    const normalized = normalizePhone(phone);
    for (const excluded of Array.from(excludedSet)) {
        if (normalizePhone(excluded) === normalized) {
            return true;
        }
    }
    return false;
};

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

interface ReturningCustomer {
    phoneNumber: string;
    contactName: string | null;
    firstCallDate: Date;
    returnCallDate: Date;
    daysBetween: number;
    totalCalls: number;
    staffName: string;
    callHistory: CallLog[];
}

const ReturningCustomersPage = () => {
    const [returningCustomers, setReturningCustomers] = useState<ReturningCustomer[]>([]);
    const [loading, setLoading] = useState(true);
    const [minDays, setMinDays] = useState(2);
    const [excludedContacts, setExcludedContacts] = useState<Set<string>>(new Set());
    const [expandedContact, setExpandedContact] = useState<string | null>(null);

    // August 1, 2025 cutoff date
    const AUG_2025_CUTOFF = new Date(2025, 7, 1, 0, 0, 0, 0).getTime();

    // Load excluded contacts from localStorage
    useEffect(() => {
        const saved = localStorage.getItem(EXCLUDED_CONTACTS_KEY);
        if (saved) {
            setExcludedContacts(new Set(JSON.parse(saved)));
        }
    }, []);

    useEffect(() => {
        fetchAndAnalyze();
    }, [minDays, excludedContacts]);

    const fetchAndAnalyze = () => {
        setLoading(true);
        apiFetch('/api/call-logs')
            .then(res => res.json())
            .then(data => {
                if (Array.isArray(data)) {
                    // Filter logs after Aug 2025 and exclude filtered contacts
                    const filteredData = data.filter((log: CallLog) => {
                        const logTime = new Date(log.timestamp).getTime();
                        if (logTime < AUG_2025_CUTOFF) return false;
                        if (isExcluded(log.phoneNumber, excludedContacts)) return false;
                        return true;
                    });

                    // Group by normalized phone number to merge +91 variants
                    const contactGroups = new Map<string, CallLog[]>();
                    filteredData.forEach((log: CallLog) => {
                        const normalized = normalizePhone(log.phoneNumber);
                        const existing = contactGroups.get(normalized) || [];
                        existing.push(log);
                        contactGroups.set(normalized, existing);
                    });

                    // Find returning customers (called again after minDays days)
                    const returning: ReturningCustomer[] = [];

                    contactGroups.forEach((logs, normalizedPhone) => {
                        if (logs.length < 2) return; // Need at least 2 calls

                        // Sort by timestamp ascending (oldest first)
                        logs.sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime());

                        const firstCall = new Date(logs[0].timestamp);
                        const lastCall = new Date(logs[logs.length - 1].timestamp);

                        // Calculate days between first and last call
                        const diffTime = lastCall.getTime() - firstCall.getTime();
                        const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));

                        if (diffDays >= minDays) {
                            // Find the first return call that's >= minDays after first call
                            let returnCall = lastCall;
                            for (let i = 1; i < logs.length; i++) {
                                const callDate = new Date(logs[i].timestamp);
                                const daysDiff = Math.floor((callDate.getTime() - firstCall.getTime()) / (1000 * 60 * 60 * 24));
                                if (daysDiff >= minDays) {
                                    returnCall = callDate;
                                    break;
                                }
                            }

                            const returnDiffDays = Math.floor((returnCall.getTime() - firstCall.getTime()) / (1000 * 60 * 60 * 24));

                            // Sort call history descending for display (newest first)
                            const callHistory = [...logs].sort((a, b) =>
                                new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
                            );

                            // Use original phone number format (prefer +91 version if exists)
                            const displayPhone = logs.find(l => l.phoneNumber.startsWith('+91'))?.phoneNumber || logs[0].phoneNumber;
                            // Get contact name from any log that has it
                            const contactName = logs.find(l => l.contactName)?.contactName || null;

                            returning.push({
                                phoneNumber: displayPhone,
                                contactName,
                                firstCallDate: firstCall,
                                returnCallDate: returnCall,
                                daysBetween: returnDiffDays,
                                totalCalls: logs.length,
                                staffName: logs[logs.length - 1].staff?.name || 'Unknown',
                                callHistory
                            });
                        }
                    });

                    // Sort by days between (descending)
                    returning.sort((a, b) => b.daysBetween - a.daysBetween);

                    setReturningCustomers(returning);
                }
                setLoading(false);
            })
            .catch(err => {
                console.error("Error fetching logs:", err);
                setLoading(false);
            });
    };

    const formatDate = (date: Date) => {
        return date.toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric',
            year: 'numeric'
        });
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
            {/* Header */}
            <header style={{
                display: 'flex',
                flexDirection: 'column',
                gap: '16px',
                marginBottom: '24px'
            }}>
                <div>
                    <h2 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '4px' }}>Returning Customers</h2>
                    <p style={{ color: '#64748b', fontSize: '0.875rem' }}>Contacts who called back after {minDays}+ days</p>
                </div>
                <div style={{
                    display: 'flex',
                    gap: '12px',
                    alignItems: 'center',
                    flexWrap: 'wrap'
                }}>
                    <label style={{ color: '#64748b', fontSize: '0.85rem' }}>Min Days:</label>
                    <select
                        value={minDays}
                        onChange={(e) => setMinDays(parseInt(e.target.value))}
                        style={{
                            background: 'rgba(255, 255, 255, 0.05)',
                            border: '1px solid var(--card-border)',
                            borderRadius: '6px',
                            color: 'white',
                            padding: '8px 12px',
                            fontSize: '0.9rem',
                            outline: 'none'
                        }}
                    >
                        <option value="2">2 days</option>
                        <option value="3">3 days</option>
                        <option value="5">5 days</option>
                        <option value="7">7 days (1 week)</option>
                        <option value="14">14 days (2 weeks)</option>
                        <option value="30">30 days (1 month)</option>
                    </select>
                    <button
                        onClick={fetchAndAnalyze}
                        disabled={loading}
                        className="btn-primary"
                        style={{ padding: '8px 16px', fontSize: '0.85rem' }}
                    >
                        {loading ? 'Loading...' : 'Refresh'}
                    </button>
                </div>
            </header>

            {/* Stats Summary */}
            <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(3, 1fr)',
                gap: '12px',
                marginBottom: '20px'
            }}>
                <div className="glass-card" style={{ padding: '16px', textAlign: 'center' }}>
                    <div style={{ fontSize: '0.75rem', color: '#22c55e', marginBottom: '4px', fontWeight: 500 }}>
                        Returning
                    </div>
                    <div style={{ fontSize: '2rem', fontWeight: 700, color: '#22c55e' }}>
                        {returningCustomers.length}
                    </div>
                </div>
                <div className="glass-card" style={{ padding: '16px', textAlign: 'center' }}>
                    <div style={{ fontSize: '0.75rem', color: '#38bdf8', marginBottom: '4px', fontWeight: 500 }}>
                        Avg Days
                    </div>
                    <div style={{ fontSize: '2rem', fontWeight: 700, color: '#38bdf8' }}>
                        {returningCustomers.length > 0
                            ? Math.round(returningCustomers.reduce((sum, c) => sum + c.daysBetween, 0) / returningCustomers.length)
                            : 0}
                    </div>
                </div>
                <div className="glass-card" style={{ padding: '16px', textAlign: 'center' }}>
                    <div style={{ fontSize: '0.75rem', color: '#f97316', marginBottom: '4px', fontWeight: 500 }}>
                        Max Days
                    </div>
                    <div style={{ fontSize: '2rem', fontWeight: 700, color: '#f97316' }}>
                        {returningCustomers.length > 0
                            ? Math.max(...returningCustomers.map(c => c.daysBetween))
                            : 0}
                    </div>
                </div>
            </div>

            {/* Customer List */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {loading ? (
                    <div className="glass-card" style={{ padding: '32px', textAlign: 'center', color: '#64748b' }}>
                        Loading...
                    </div>
                ) : returningCustomers.length === 0 ? (
                    <div className="glass-card" style={{ padding: '32px', textAlign: 'center', color: '#64748b' }}>
                        No returning customers found with {minDays}+ days gap
                    </div>
                ) : (
                    returningCustomers.map((customer) => {
                        const isExpanded = expandedContact === customer.phoneNumber;
                        return (
                            <div key={customer.phoneNumber} className="glass-card" style={{ overflow: 'hidden' }}>
                                {/* Clickable Header */}
                                <div
                                    onClick={() => setExpandedContact(isExpanded ? null : customer.phoneNumber)}
                                    style={{
                                        padding: '14px 16px',
                                        cursor: 'pointer',
                                        background: isExpanded ? 'rgba(56, 189, 248, 0.05)' : 'transparent'
                                    }}
                                >
                                    {/* Top Row: Name + Days Badge */}
                                    <div style={{
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        alignItems: 'flex-start',
                                        marginBottom: '10px'
                                    }}>
                                        <div style={{ flex: 1 }}>
                                            <div style={{
                                                fontSize: '1rem',
                                                fontWeight: 600,
                                                color: '#1a1a1a',
                                                marginBottom: '2px'
                                            }}>
                                                {customer.contactName || customer.phoneNumber}
                                            </div>
                                            {customer.contactName && (
                                                <div style={{ fontSize: '0.8rem', color: '#38bdf8' }}>
                                                    {customer.phoneNumber}
                                                </div>
                                            )}
                                        </div>
                                        <div style={{
                                            background: 'linear-gradient(135deg, #22c55e, #16a34a)',
                                            color: 'white',
                                            padding: '6px 12px',
                                            borderRadius: '20px',
                                            fontSize: '0.85rem',
                                            fontWeight: 700
                                        }}>
                                            {customer.daysBetween} days
                                        </div>
                                    </div>

                                    {/* Stats Grid */}
                                    <div style={{
                                        display: 'grid',
                                        gridTemplateColumns: 'repeat(3, 1fr)',
                                        gap: '8px',
                                        marginBottom: '8px'
                                    }}>
                                        <div style={{ textAlign: 'center' }}>
                                            <div style={{ fontSize: '0.65rem', color: '#64748b' }}>First Call</div>
                                            <div style={{ fontSize: '0.8rem', fontWeight: 500, color: '#1a1a1a' }}>
                                                {formatDate(customer.firstCallDate)}
                                            </div>
                                        </div>
                                        <div style={{ textAlign: 'center' }}>
                                            <div style={{ fontSize: '0.65rem', color: '#64748b' }}>Return Call</div>
                                            <div style={{ fontSize: '0.8rem', fontWeight: 500, color: '#22c55e' }}>
                                                {formatDate(customer.returnCallDate)}
                                            </div>
                                        </div>
                                        <div style={{ textAlign: 'center' }}>
                                            <div style={{ fontSize: '0.65rem', color: '#64748b' }}>Total Calls</div>
                                            <div style={{ fontSize: '0.8rem', fontWeight: 500, color: '#38bdf8' }}>
                                                {customer.totalCalls}
                                            </div>
                                        </div>
                                    </div>

                                    {/* Staff + Expand indicator */}
                                    <div style={{
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        alignItems: 'center'
                                    }}>
                                        <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
                                            Staff: <span style={{ color: '#1a1a1a', fontWeight: 500 }}>{customer.staffName}</span>
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
                                            Call History ({customer.callHistory.length} calls)
                                        </div>
                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                            {customer.callHistory.map((call) => (
                                                <div
                                                    key={call.id}
                                                    style={{
                                                        padding: '10px 12px',
                                                        background: 'rgba(255, 255, 255, 0.02)',
                                                        borderRadius: '6px',
                                                        borderLeft: `3px solid ${
                                                            call.callType === 'OUTGOING' ? '#38bdf8' :
                                                            call.callType === 'INCOMING' ? '#22c55e' : '#ef4444'
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
                                                            background: call.callType === 'OUTGOING'
                                                                ? 'rgba(56, 189, 248, 0.15)'
                                                                : call.callType === 'INCOMING'
                                                                    ? 'rgba(34, 197, 94, 0.15)'
                                                                    : 'rgba(239, 68, 68, 0.15)',
                                                            color: call.callType === 'OUTGOING'
                                                                ? '#38bdf8'
                                                                : call.callType === 'INCOMING'
                                                                    ? '#22c55e'
                                                                    : '#ef4444'
                                                        }}>
                                                            {call.callType}
                                                        </span>
                                                        <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
                                                            {new Date(call.timestamp).toLocaleString()}
                                                        </span>
                                                        <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
                                                            {formatDuration(call.duration)}
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

export default ReturningCustomersPage;

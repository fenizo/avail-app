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

interface ReturningCustomer {
    phoneNumber: string;
    contactName: string | null;
    firstCallDate: Date;
    returnCallDate: Date;
    daysBetween: number;
    totalCalls: number;
    staffName: string;
}

const ReturningCustomersPage = () => {
    const [returningCustomers, setReturningCustomers] = useState<ReturningCustomer[]>([]);
    const [loading, setLoading] = useState(true);
    const [minDays, setMinDays] = useState(2);

    // August 1, 2025 cutoff date
    const AUG_2025_CUTOFF = new Date(2025, 7, 1, 0, 0, 0, 0).getTime();

    useEffect(() => {
        fetchAndAnalyze();
    }, [minDays]);

    const fetchAndAnalyze = () => {
        setLoading(true);
        apiFetch('/api/call-logs')
            .then(res => res.json())
            .then(data => {
                if (Array.isArray(data)) {
                    // Filter logs after Aug 2025
                    const filteredData = data.filter((log: CallLog) => {
                        const logTime = new Date(log.timestamp).getTime();
                        return logTime >= AUG_2025_CUTOFF;
                    });

                    // Group by phone number
                    const contactGroups = new Map<string, CallLog[]>();
                    filteredData.forEach((log: CallLog) => {
                        const existing = contactGroups.get(log.phoneNumber) || [];
                        existing.push(log);
                        contactGroups.set(log.phoneNumber, existing);
                    });

                    // Find returning customers (called again after minDays days)
                    const returning: ReturningCustomer[] = [];

                    contactGroups.forEach((logs, phoneNumber) => {
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

                            returning.push({
                                phoneNumber,
                                contactName: logs[0].contactName || null,
                                firstCallDate: firstCall,
                                returnCallDate: returnCall,
                                daysBetween: returnDiffDays,
                                totalCalls: logs.length,
                                staffName: logs[logs.length - 1].staff?.name || 'Unknown'
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
                    returningCustomers.map((customer, index) => (
                        <div key={customer.phoneNumber} className="glass-card" style={{ padding: '14px 16px' }}>
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

                            {/* Staff */}
                            <div style={{ fontSize: '0.75rem', color: '#64748b' }}>
                                Staff: <span style={{ color: '#1a1a1a', fontWeight: 500 }}>{customer.staffName}</span>
                            </div>
                        </div>
                    ))
                )}
            </div>
        </div>
    );
};

export default ReturningCustomersPage;

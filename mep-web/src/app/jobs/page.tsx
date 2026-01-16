"use client";

import React, { useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api';

export default function JobsPage() {
    const [jobs, setJobs] = useState<any[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        apiFetch('/api/jobs')
            .then(res => res.json())
            .then(data => {
                if (Array.isArray(data)) {
                    setJobs(data);
                }
                setLoading(false);
            })
            .catch(err => {
                console.error("Failed to fetch jobs:", err);
                setLoading(false);
            });
    }, []);

    return (
        <div>
            <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '32px' }}>
                <div>
                    <h2 style={{ fontSize: '2rem', fontWeight: 700 }}>Job Management</h2>
                    <p style={{ color: '#94a3b8' }}>Create and assign jobs to field staff.</p>
                </div>
                <button className="btn-primary">+ Create New Job</button>
            </header>

            {loading ? (
                <div style={{ textAlign: 'center', padding: '40px', color: '#94a3b8' }}>Loading jobs...</div>
            ) : (
                <div style={{ display: 'grid', gap: '20px' }}>
                    {jobs.length === 0 ? (
                        <div className="glass-card" style={{ padding: '40px', textAlign: 'center', color: '#94a3b8' }}>No jobs found.</div>
                    ) : (
                        jobs.map((job) => (
                            <div key={job.id} className="glass-card" style={{ padding: '20px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                <div style={{ display: 'flex', gap: '24px' }}>
                                    <div>
                                        <div style={{ fontSize: '0.875rem', color: '#94a3b8' }}>Customer</div>
                                        <div style={{ fontWeight: 600 }}>{job.customer?.name || 'Unknown'}</div>
                                    </div>
                                    <div>
                                        <div style={{ fontSize: '0.875rem', color: '#94a3b8' }}>Type</div>
                                        <div style={{ fontWeight: 600 }}>{job.serviceType}</div>
                                    </div>
                                    <div>
                                        <div style={{ fontSize: '0.875rem', color: '#94a3b8' }}>Staff</div>
                                        <div style={{ fontWeight: 600 }}>{job.staff?.name || 'Unassigned'}</div>
                                    </div>
                                    <div>
                                        <div style={{ fontSize: '0.875rem', color: '#94a3b8' }}>Created</div>
                                        <div style={{ fontWeight: 600 }}>{new Date(job.createdAt).toLocaleDateString()}</div>
                                    </div>
                                </div>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                                    <span style={{
                                        padding: '4px 10px',
                                        borderRadius: '20px',
                                        fontSize: '0.75rem',
                                        background: job.status === 'COMPLETED' ? 'rgba(34, 197, 94, 0.1)' : 'rgba(56, 189, 248, 0.1)',
                                        color: job.status === 'COMPLETED' ? '#22c55e' : 'var(--primary)'
                                    }}>
                                        {job.status}
                                    </span>
                                    <button className="btn-primary" style={{ padding: '6px 12px', fontSize: '0.8rem', background: 'transparent', border: '1px solid var(--primary)', color: 'var(--primary)' }}>Edit Details</button>
                                </div>
                            </div>
                        ))
                    )
                    }
                </div>
            )}
        </div>
    );
}

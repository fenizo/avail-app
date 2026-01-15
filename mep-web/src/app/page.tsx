"use client";

import React, { useEffect, useState } from 'react';

interface JobItem {
    description: string;
    quantity: number;
    price: number;
}

interface Job {
    id: string;
    customer: {
        name: string;
    };
    staff: {
        username: string;
    };
    serviceType: string;
    status: string;
    items: JobItem[];
    createdAt: string;
}

export default function Dashboard() {
    const [jobs, setJobs] = useState<Job[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        fetch('/api/jobs')
            .then(res => res.json())
            .then(data => {
                setJobs(data);
                setLoading(false);
            })
            .catch(err => {
                console.error("Failed to fetch jobs:", err);
                setLoading(false);
            });
    }, []);

    const totalCalls = jobs.length;
    const completedJobs = jobs.filter(j => j.status === 'COMPLETED').length;
    const pendingJobs = jobs.filter(j => j.status === 'NEW' || j.status === 'IN_PROGRESS').length;

    const revenue = jobs
        .filter(j => j.status === 'COMPLETED') // Assuming revenue is only realized on completion
        .reduce((sum, job) => {
            const jobTotal = job.items.reduce((itemSum, item) => itemSum + (item.price * item.quantity), 0);
            return sum + jobTotal;
        }, 0);

    const metrics = [
        { label: 'Total Calls Today', value: totalCalls.toString(), color: 'var(--primary)' }, // Showing total for now
        { label: 'Completed Jobs', value: completedJobs.toString(), color: 'var(--success)' },
        { label: 'Pending Jobs', value: pendingJobs.toString(), color: 'var(--warning)' },
        { label: 'Revenue Today', value: `₹${revenue.toLocaleString()}`, color: 'var(--accent)' },
    ];

    return (
        <div>
            <header style={{ marginBottom: '32px' }}>
                <h2 style={{ fontSize: '2rem', fontWeight: 700 }}>Admin Dashboard</h2>
                <p style={{ color: '#94a3b8' }}>Welcome back, here is what is happening today.</p>
            </header>

            <div className="metric-grid">
                {metrics.map((m) => (
                    <div key={m.label} className="glass-card metric-card">
                        <span className="metric-label">{m.label}</span>
                        <span className="metric-value" style={{ color: m.color }}>{m.value}</span>
                    </div>
                ))}
            </div>

            <section className="glass-card" style={{ padding: '24px' }}>
                <h3 style={{ marginBottom: '20px', fontSize: '1.25rem' }}>Recent Active Jobs</h3>
                {loading ? (
                    <p>Loading jobs...</p>
                ) : (
                    <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                        <thead>
                            <tr style={{ textAlign: 'left', borderBottom: '1px solid var(--card-border)', color: '#94a3b8' }}>
                                <th style={{ padding: '12px' }}>Customer</th>
                                <th style={{ padding: '12px' }}>Service</th>
                                <th style={{ padding: '12px' }}>Staff</th>
                                <th style={{ padding: '12px' }}>Status</th>
                                <th style={{ padding: '12px' }}>Action</th>
                            </tr>
                        </thead>
                        <tbody>
                            {jobs.slice(0, 10).map((job) => (
                                <tr key={job.id} style={{ borderBottom: '1px solid var(--card-border)' }}>
                                    <td style={{ padding: '12px' }}>{job.customer?.name || "Unknown"}</td>
                                    <td style={{ padding: '12px' }}>{job.serviceType}</td>
                                    <td style={{ padding: '12px' }}>{job.staff?.username || "Unassigned"}</td>
                                    <td style={{ padding: '12px' }}>
                                        <span style={{
                                            padding: '4px 10px',
                                            borderRadius: '20px',
                                            fontSize: '0.75rem',
                                            background: job.status === 'IN_PROGRESS' ? 'rgba(56, 189, 248, 0.1)' :
                                                job.status === 'COMPLETED' ? 'rgba(34, 197, 94, 0.1)' :
                                                    'rgba(245, 158, 11, 0.1)',
                                            color: job.status === 'IN_PROGRESS' ? 'var(--primary)' :
                                                job.status === 'COMPLETED' ? 'var(--success)' :
                                                    'var(--warning)'
                                        }}>
                                            {job.status}
                                        </span>
                                    </td>
                                    <td style={{ padding: '12px' }}>
                                        <button className="btn-primary" style={{ padding: '6px 12px', fontSize: '0.8rem' }}>View</button>
                                    </td>
                                </tr>
                            ))}
                            {jobs.length === 0 && (
                                <tr>
                                    <td colSpan={5} style={{ padding: '24px', textAlign: 'center', color: '#94a3b8' }}>No active jobs found.</td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                )}
            </section>
        </div>
    );
}

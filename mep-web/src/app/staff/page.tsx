"use client";

import React, { useState, useEffect } from 'react';
import { apiFetch } from '../../lib/api';

interface StaffMember {
    id: string;
    name: string;
    phone: string;
    role: string;
}

export default function StaffPage() {
    const [staff, setStaff] = useState<StaffMember[]>([]);
    const [loading, setLoading] = useState(true);
    const [showModal, setShowModal] = useState(false);
    const [newStaff, setNewStaff] = useState({ name: '', phone: '', password: '' });
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        fetchStaff();
    }, []);

    const fetchStaff = async () => {
        try {
            const res = await apiFetch('/api/staff');
            if (res.ok) {
                const data = await res.json();
                setStaff(data);
            }
        } catch (error) {
            console.error("Failed to fetch staff", error);
        } finally {
            setLoading(false);
        }
    };

    const handleCreate = async (e: React.FormEvent) => {
        e.preventDefault();
        setSaving(true);
        try {
            const res = await apiFetch('/api/staff', {
                method: 'POST',
                body: JSON.stringify(newStaff)
            });
            if (res.ok) {
                setShowModal(false);
                setNewStaff({ name: '', phone: '', password: '' });
                fetchStaff();
            } else {
                const err = await res.json();
                alert(err.message || "Failed to create staff");
            }
        } catch (error) {
            alert("An error occurred");
        } finally {
            setSaving(false);
        }
    };

    const handleDelete = async (id: string) => {
        if (!confirm("Are you sure you want to remove this staff member?")) return;
        try {
            const res = await apiFetch(`/api/staff/${id}`, { method: 'DELETE' });
            if (res.ok) fetchStaff();
        } catch (error) {
            console.error(error);
        }
    };

    return (
        <div className="animate-enter">
            <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '32px' }}>
                <div>
                    <h2 style={{ fontSize: '2rem', fontWeight: 700, marginBottom: '4px' }}>Staff Management</h2>
                    <p style={{ color: '#94a3b8' }}>Manage field technicians and their access.</p>
                </div>
                <button onClick={() => setShowModal(true)} className="btn-primary" style={{ padding: '12px 24px' }}>
                    + Create Staff
                </button>
            </header>

            <div className="glass-card" style={{ overflow: 'hidden' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                    <thead>
                        <tr style={{ background: 'rgba(255, 255, 255, 0.02)', borderBottom: '1px solid var(--card-border)' }}>
                            <th style={{ padding: '16px 24px', color: '#94a3b8', fontSize: '0.875rem', fontWeight: 500 }}>Name</th>
                            <th style={{ padding: '16px 24px', color: '#94a3b8', fontSize: '0.875rem', fontWeight: 500 }}>Phone</th>
                            <th style={{ padding: '16px 24px', color: '#94a3b8', fontSize: '0.875rem', fontWeight: 500 }}>Role</th>
                            <th style={{ padding: '16px 24px', color: '#94a3b8', fontSize: '0.875rem', fontWeight: 500, textAlign: 'right' }}>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        {loading ? (
                            <tr><td colSpan={4} style={{ padding: '48px', textAlign: 'center', color: '#475569' }}>Loading staff list...</td></tr>
                        ) : staff.length === 0 ? (
                            <tr><td colSpan={4} style={{ padding: '48px', textAlign: 'center', color: '#475569' }}>No staff members found.</td></tr>
                        ) : (
                            staff.map((s) => (
                                <tr key={s.id} style={{ borderBottom: '1px solid var(--card-border)' }}>
                                    <td style={{ padding: '16px 24px', fontWeight: 500 }}>{s.name}</td>
                                    <td style={{ padding: '16px 24px', color: '#38bdf8' }}>{s.phone}</td>
                                    <td style={{ padding: '16px 24px' }}>
                                        <span style={{
                                            padding: '4px 8px',
                                            borderRadius: '4px',
                                            fontSize: '0.7rem',
                                            background: 'rgba(56, 189, 248, 0.1)',
                                            color: '#38bdf8'
                                        }}>
                                            {s.role}
                                        </span>
                                    </td>
                                    <td style={{ padding: '16px 24px', textAlign: 'right' }}>
                                        <button
                                            onClick={() => handleDelete(s.id)}
                                            style={{ color: '#ef4444', background: 'none', border: 'none', cursor: 'pointer', fontSize: '0.85rem' }}
                                        >
                                            Remove
                                        </button>
                                    </td>
                                </tr>
                            ))
                        )}
                    </tbody>
                </table>
            </div>

            {showModal && (
                <div style={{
                    position: 'fixed', top: 0, left: 0, width: '100%', height: '100%',
                    background: 'rgba(0,0,0,0.8)', display: 'flex', justifyContent: 'center', alignItems: 'center',
                    zIndex: 1000, backdropFilter: 'blur(4px)'
                }}>
                    <div className="glass-card animate-enter" style={{ width: '400px', padding: '32px' }}>
                        <h3 style={{ fontSize: '1.5rem', fontWeight: 600, marginBottom: '24px' }}>New Staff Member</h3>
                        <form onSubmit={handleCreate}>
                            <div style={{ marginBottom: '16px' }}>
                                <label style={{ display: 'block', fontSize: '0.85rem', color: '#94a3b8', marginBottom: '8px' }}>Full Name</label>
                                <input
                                    required
                                    className="input-field"
                                    style={{ width: '100%', padding: '12px', background: 'rgba(255,255,255,0.05)', border: '1px solid var(--card-border)', borderRadius: '8px', color: 'white' }}
                                    value={newStaff.name}
                                    onChange={e => setNewStaff({ ...newStaff, name: e.target.value })}
                                />
                            </div>
                            <div style={{ marginBottom: '16px' }}>
                                <label style={{ display: 'block', fontSize: '0.85rem', color: '#94a3b8', marginBottom: '8px' }}>Phone Number</label>
                                <input
                                    required
                                    className="input-field"
                                    style={{ width: '100%', padding: '12px', background: 'rgba(255,255,255,0.05)', border: '1px solid var(--card-border)', borderRadius: '8px', color: 'white' }}
                                    value={newStaff.phone}
                                    onChange={e => setNewStaff({ ...newStaff, phone: e.target.value })}
                                />
                            </div>
                            <div style={{ marginBottom: '32px' }}>
                                <label style={{ display: 'block', fontSize: '0.85rem', color: '#94a3b8', marginBottom: '8px' }}>Login Password</label>
                                <input
                                    required
                                    type="password"
                                    className="input-field"
                                    style={{ width: '100%', padding: '12px', background: 'rgba(255,255,255,0.05)', border: '1px solid var(--card-border)', borderRadius: '8px', color: 'white' }}
                                    value={newStaff.password}
                                    onChange={e => setNewStaff({ ...newStaff, password: e.target.value })}
                                />
                            </div>
                            <div style={{ display: 'flex', gap: '12px' }}>
                                <button type="submit" disabled={saving} className="btn-primary" style={{ flex: 1, padding: '12px' }}>
                                    {saving ? 'Creating...' : 'Create Account'}
                                </button>
                                <button type="button" onClick={() => setShowModal(false)} style={{ flex: 1, background: 'rgba(255,255,255,0.05)', border: 'none', color: 'white', borderRadius: '8px', cursor: 'pointer' }}>
                                    Cancel
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}

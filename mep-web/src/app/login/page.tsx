"use client";

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';

export default function LoginPage() {
    const [phone, setPhone] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const router = useRouter();

    const handleLogin = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setError('');

        try {
            const res = await fetch('/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ phone, password }),
            });

            if (res.ok) {
                const data = await res.json();
                localStorage.setItem('token', data.token);
                localStorage.setItem('userRole', data.role);
                router.push('/');
            } else {
                setError('Invalid credentials or unauthorized access.');
            }
        } catch (err) {
            setError('Connection failed. Please check the backend.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={{
            height: '100vh',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            background: 'radial-gradient(circle at top right, #1e293b, #0f172a)',
            padding: '24px'
        }}>
            <div className="glass-card" style={{
                width: '100%',
                maxWidth: '400px',
                padding: '40px',
                textAlign: 'center'
            }}>
                <h1 className="gradient-text" style={{ fontSize: '2.5rem', marginBottom: '8px', fontWeight: 800 }}>Avawke</h1>
                <p style={{ color: '#94a3b8', marginBottom: '32px' }}>Admin Panel Authentication</p>

                <form onSubmit={handleLogin} style={{ textAlign: 'left' }}>
                    <div style={{ marginBottom: '20px' }}>
                        <label style={{ display: 'block', color: '#94a3b8', marginBottom: '8px', fontSize: '0.9rem' }}>Phone Number</label>
                        <input
                            type="text"
                            value={phone}
                            onChange={(e) => setPhone(e.target.value)}
                            placeholder="e.g. 9999999999"
                            required
                            style={{
                                width: '100%',
                                padding: '12px 16px',
                                background: 'rgba(255, 255, 255, 0.05)',
                                border: '1px solid var(--card-border)',
                                borderRadius: '8px',
                                color: 'white',
                                outline: 'none'
                            }}
                        />
                    </div>
                    <div style={{ marginBottom: '32px' }}>
                        <label style={{ display: 'block', color: '#94a3b8', marginBottom: '8px', fontSize: '0.9rem' }}>Password</label>
                        <input
                            type="password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            placeholder="••••••••"
                            required
                            style={{
                                width: '100%',
                                padding: '12px 16px',
                                background: 'rgba(255, 255, 255, 0.05)',
                                border: '1px solid var(--card-border)',
                                borderRadius: '8px',
                                color: 'white',
                                outline: 'none'
                            }}
                        />
                    </div>

                    {error && <p style={{ color: '#f43f5e', fontSize: '0.85rem', marginBottom: '20px', textAlign: 'center' }}>{error}</p>}

                    <button
                        type="submit"
                        disabled={loading}
                        className="btn-primary"
                        style={{ width: '100%', padding: '14px', borderRadius: '12px', fontSize: '1rem' }}
                    >
                        {loading ? 'Authenticating...' : 'Sign In'}
                    </button>

                    <p style={{ color: '#475569', fontSize: '0.75rem', marginTop: '24px', textAlign: 'center' }}>
                        Demo Admin: 9999999999 / 10006
                    </p>
                </form>
            </div>
        </div>
    );
}

"use client";

import './globals.css'
import React, { useEffect, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  const pathname = usePathname();
  const router = useRouter();
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token && pathname !== '/login') {
      router.push('/login');
    } else {
      setIsAuthenticated(true);
    }
  }, [pathname, router]);

  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('userRole');
    router.push('/login');
  };

  if (pathname === '/login') {
    return (
      <html lang="en">
        <body>{children}</body>
      </html>
    );
  }

  return (
    <html lang="en">
      <body>
        <div className="sidebar">
          <h1 className="gradient-text" style={{ fontSize: '1.5rem', marginBottom: '32px' }}>MEP Pro</h1>
          <nav style={{ display: 'flex', flexDirection: 'column', gap: '16px', flex: 1 }}>
            <a href="/" style={{ color: pathname === '/' ? 'var(--primary)' : '#94a3b8', textDecoration: 'none', fontWeight: pathname === '/' ? 600 : 400 }}>Dashboard</a>
            <a href="/jobs" style={{ color: pathname === '/jobs' ? 'var(--primary)' : '#94a3b8', textDecoration: 'none' }}>Jobs</a>
            <a href="/call-logs" style={{ color: pathname === '/call-logs' ? 'var(--primary)' : '#94a3b8', textDecoration: 'none' }}>Call Logs</a>
            <a href="/customers" style={{ color: pathname === '/customers' ? 'var(--primary)' : '#94a3b8', textDecoration: 'none' }}>Customers</a>
            <a href="/staff" style={{ color: pathname === '/staff' ? 'var(--primary)' : '#94a3b8', textDecoration: 'none' }}>Staff</a>
            <a href="/settings" style={{ color: pathname === '/settings' ? 'var(--primary)' : '#94a3b8', textDecoration: 'none' }}>Settings</a>
          </nav>

          <div style={{ marginTop: 'auto', borderTop: '1px solid var(--card-border)', paddingTop: '20px' }}>
            <button
              onClick={handleLogout}
              style={{
                background: 'none',
                border: 'none',
                color: '#f43f5e',
                cursor: 'pointer',
                fontSize: '0.9rem',
                display: 'flex',
                alignItems: 'center',
                gap: '8px'
              }}
            >
              Sign Out
            </button>
          </div>
        </div>
        <main className="main-content">
          {children}
        </main>
      </body>
    </html>
  )
}

"use client";

import './globals.css'
import React, { useEffect, useState, ReactNode } from 'react';
import { usePathname, useRouter } from 'next/navigation';

export default function RootLayout({
  children,
}: {
  children: ReactNode
}) {
  const pathname = usePathname();
  const router = useRouter();
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token && pathname !== '/login') {
      router.push('/login');
    } else {
      setIsAuthenticated(true);
    }
  }, [pathname, router]);

  // Close menu on navigation
  useEffect(() => {
    setIsMobileMenuOpen(false);
  }, [pathname]);

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
        {/* Mobile Header */}
        <div className="mobile-header">
          <h1 className="gradient-text" style={{ fontSize: '1.25rem' }}>Avawke</h1>
          <button
            onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
            style={{
              background: 'none',
              border: 'none',
              color: 'white',
              fontSize: '1.5rem',
              cursor: 'pointer',
              padding: '8px'
            }}
          >
            {isMobileMenuOpen ? '✕' : '☰'}
          </button>
        </div>

        {/* Sidebar Overlay */}
        {isMobileMenuOpen && (
          <div className="sidebar-overlay" onClick={() => setIsMobileMenuOpen(false)} />
        )}

        <div className={`sidebar ${isMobileMenuOpen ? 'open' : ''}`}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '32px' }}>
            <h1 className="gradient-text" style={{ fontSize: '1.5rem' }}>Avawke</h1>
          </div>

          <nav style={{ display: 'flex', flexDirection: 'column', gap: '16px', flex: 1 }}>
            <a href="/" style={{ color: pathname === '/' ? 'var(--primary)' : '#64748b', textDecoration: 'none', fontWeight: pathname === '/' ? 600 : 400 }}>Dashboard</a>
            <a href="/jobs" style={{ color: pathname === '/jobs' ? 'var(--primary)' : '#64748b', textDecoration: 'none', fontWeight: pathname === '/jobs' ? 600 : 400 }}>Jobs</a>
            <a href="/call-logs" style={{ color: pathname === '/call-logs' ? 'var(--primary)' : '#64748b', textDecoration: 'none', fontWeight: pathname === '/call-logs' ? 600 : 400 }}>Call Logs</a>
            <a href="/returning-customers" style={{ color: pathname === '/returning-customers' ? 'var(--primary)' : '#64748b', textDecoration: 'none', fontWeight: pathname === '/returning-customers' ? 600 : 400 }}>Returning Customers</a>
            <a href="/customers" style={{ color: pathname === '/customers' ? 'var(--primary)' : '#64748b', textDecoration: 'none', fontWeight: pathname === '/customers' ? 600 : 400 }}>Customers</a>
            <a href="/staff" style={{ color: pathname === '/staff' ? 'var(--primary)' : '#64748b', textDecoration: 'none', fontWeight: pathname === '/staff' ? 600 : 400 }}>Staff</a>
            <a href="/settings" style={{ color: pathname === '/settings' ? 'var(--primary)' : '#64748b', textDecoration: 'none', fontWeight: pathname === '/settings' ? 600 : 400 }}>Settings</a>
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

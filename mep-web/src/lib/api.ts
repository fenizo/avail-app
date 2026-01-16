"use client";

export async function apiFetch(url: string, options: RequestInit = {}) {
    const token = typeof window !== 'undefined' ? localStorage.getItem('token') : null;

    const headers = {
        'Content-Type': 'application/json',
        ...options.headers,
    } as Record<string, string>;

    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(url, {
        ...options,
        headers,
    });

    if (response.status === 401 || response.status === 403) {
        // Only redirect if we're in the browser and not already on the login page
        if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
            localStorage.removeItem('token');
            window.location.href = '/login';
        }
    }

    return response;
}

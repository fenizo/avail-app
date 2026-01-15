/** @type {import('next').NextConfig} */
const nextConfig = {
    async rewrites() {
        return [
            {
                source: '/api/:path*',
                destination: 'http://72.62.193.110:8085/api/:path*',
            },
        ];
    },
};

module.exports = nextConfig;

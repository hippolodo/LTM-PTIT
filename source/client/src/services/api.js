import axios from 'axios'
export const api = axios.create({baseURL:import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081',timeout:5000})
export async function health() { return (await api.get('/api/health')).data }

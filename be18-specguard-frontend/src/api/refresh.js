import axios from "axios";

// ✅ refresh 전용 인스턴스 (Authorization 헤더 안 붙음)
const refreshApi = axios.create({
    baseURL: `${import.meta.env.VITE_API_BASE_URL}/api/v1`,
    withCredentials: true, // refresh_token 쿠키 전송
});

export default refreshApi;
import axios from "axios";

const axiosInstance = axios.create({
  baseURL: process.env.VITE_SERVER_BASE_URL
});

export default axiosInstance;

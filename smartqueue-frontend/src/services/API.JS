import axios from 'axios';

const BASE = 'http://localhost:8080/api';

const api = axios.create({
    baseURL: BASE,
    withCredentials: true,
});

export const generateToken = (serviceType, priorityFlag, officeId) =>
    api.post('/token', { serviceType, priorityFlag, officeId });

export const getQueueState = (officeId) =>
    api.get(`/queue/${officeId}`);

export const callNext = (officeId) =>
    api.post(`/staff/next?officeId=${officeId}`);

export const completeToken = (tokenId) =>
    api.post(`/staff/complete/${tokenId}`);

export const markNoShow = (tokenId, officeId) =>
    api.post(`/staff/noshow/${tokenId}?officeId=${officeId}`);

export const staffOverride = (tokenNumber, officeId) =>
    api.post(`/staff/override?tokenNumber=${tokenNumber}&officeId=${officeId}`);


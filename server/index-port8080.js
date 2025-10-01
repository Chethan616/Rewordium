const express = require('express');
const admin = require('firebase-admin');
const serviceAccount = require('../config/service-account-key.json');
const cors = require('cors');
require('dotenv').config();

const app = express();
const PORT = 8080; // Using different port to bypass potential firewall issues
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'sendpushnotis';

// Initialize Firebase Admin SDK
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

app.use(cors());
app.use(express.json());

// Middleware to check admin password
const authenticateAdmin = (req, res, next) => {
  const authHeader = req.headers.authorization;
  
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Unauthorized: Missing or invalid token' });
  }

  const token = authHeader.split(' ')[1];
  
  if (token !== ADMIN_PASSWORD) {
    return res.status(403).json({ error: 'Forbidden: Invalid admin password' });
  }
  
  next();
};

// Send notification endpoint
app.post('/api/send-notification', authenticateAdmin, async (req, res) => {
  try {
    const { title, body, topic = 'all_users' } = req.body;

    if (!title || !body) {
      return res.status(400).json({ error: 'Title and body are required' });
    }

    const message = {
      notification: {
        title,
        body,
      },
      android: {
        notification: {
          channelId: 'rewordium_channel',
          sound: 'default',
        },
      },
      topic,
    };

    const response = await admin.messaging().send(message);
    
    res.json({
      success: true,
      message: 'Notification sent successfully',
      messageId: response,
    });
  } catch (error) {
    console.error('Error sending message:', error);
    res.status(500).json({ 
      success: false, 
      error: error.message || 'Failed to send notification' 
    });
  }
});

// Test endpoint
app.get('/', (req, res) => {
  res.send('🚀 Rewordium Notification Server (Port 8080) - FIREWALL BYPASS VERSION');
});

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({ 
    status: 'healthy', 
    port: PORT, 
    timestamp: new Date().toISOString(),
    server: 'firewall-bypass-version'
  });
});

// Start the server on all interfaces
app.listen(PORT, '0.0.0.0', () => {
  console.log(`🔥 FIREWALL BYPASS SERVER RUNNING`);
  console.log(`📍 Local: http://localhost:${PORT}`);
  console.log(`🌐 Network: http://172.17.92.238:${PORT}`);
  console.log(`🎯 API Endpoint: http://172.17.92.238:${PORT}/api/send-notification`);
  console.log(`⚡ Health Check: http://172.17.92.238:${PORT}/health`);
  console.log(`\n⚠️  UPDATE YOUR FLUTTER APP TO USE PORT ${PORT}!`);
});

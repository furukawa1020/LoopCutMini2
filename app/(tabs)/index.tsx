import React, { useState, useEffect } from 'react';
import { Alert, StyleSheet, Switch, TouchableOpacity, NativeModules } from 'react-native';

import { ThemedText } from '@/components/ThemedText';
import { ThemedView } from '@/components/ThemedView';
import { Colors } from '@/constants/Colors';
import { useColorScheme } from '@/hooks/useColorScheme';

const { LoopCutModule } = NativeModules;

interface ServiceStatus {
  isRunning: boolean;
  detectedLoops: number;
  lastDetection: string | null;
}

export default function HomeScreen() {
  const [isServiceRunning, setIsServiceRunning] = useState(false);
  const [serviceStatus, setServiceStatus] = useState<ServiceStatus>({
    isRunning: false,
    detectedLoops: 0,
    lastDetection: null
  });
  const [isLoading, setIsLoading] = useState(false);
  const colorScheme = useColorScheme();

  useEffect(() => {
    checkServiceStatus();
    const interval = setInterval(checkServiceStatus, 2000);
    return () => clearInterval(interval);
  }, []);

  const checkServiceStatus = async () => {
    try {
      if (LoopCutModule && LoopCutModule.getServiceStatus) {
        const status = await LoopCutModule.getServiceStatus();
        setServiceStatus(status);
        setIsServiceRunning(status.isRunning);
      }
    } catch (error) {
      console.error('Failed to check service status:', error);
    }
  };

  const toggleService = async () => {
    if (isLoading) return;
    
    setIsLoading(true);
    try {
      if (isServiceRunning) {
        await LoopCutModule.stopService();
        setIsServiceRunning(false);
      } else {
        const hasPermission = await requestPermissions();
        if (hasPermission) {
          await LoopCutModule.startService();
          setIsServiceRunning(true);
        }
      }
    } catch (error) {
      console.error('Failed to toggle service:', error);
      Alert.alert('エラー', 'サービスの操作に失敗しました');
    } finally {
      setIsLoading(false);
    }
  };

  const requestPermissions = async (): Promise<boolean> => {
    try {
      if (LoopCutModule && LoopCutModule.requestPermissions) {
        return await LoopCutModule.requestPermissions();
      }
      return false;
    } catch (error) {
      console.error('Permission request failed:', error);
      return false;
    }
  };

  const resetStats = async () => {
    try {
      if (LoopCutModule && LoopCutModule.resetStats) {
        await LoopCutModule.resetStats();
        checkServiceStatus();
      }
    } catch (error) {
      console.error('Failed to reset stats:', error);
    }
  };

  return (
    <ThemedView style={styles.container}>
      <ThemedView style={styles.header}>
        <ThemedText type="title" style={styles.title}>LoopCut Mini</ThemedText>
        <ThemedText style={styles.subtitle}>
          ネガティブな思考パターンを検出してサポートします
        </ThemedText>
      </ThemedView>

      <ThemedView style={[styles.card, { backgroundColor: Colors[colorScheme ?? 'light'].background }]}>
        <ThemedView style={styles.serviceControl}>
          <ThemedText type="subtitle">音声監視</ThemedText>
          <Switch
            value={isServiceRunning}
            onValueChange={toggleService}
            disabled={isLoading}
            trackColor={{ false: '#767577', true: '#81b0ff' }}
            thumbColor={isServiceRunning ? '#f5dd4b' : '#f4f3f4'}
          />
        </ThemedView>
        
        <ThemedText style={styles.statusText}>
          状態: {isServiceRunning ? '監視中' : '停止中'}
        </ThemedText>
      </ThemedView>

      <ThemedView style={[styles.card, { backgroundColor: Colors[colorScheme ?? 'light'].background }]}>
        <ThemedText type="subtitle" style={styles.statsTitle}>統計情報</ThemedText>
        
        <ThemedView style={styles.statRow}>
          <ThemedText style={styles.statLabel}>検出回数:</ThemedText>
          <ThemedText style={styles.statValue}>{serviceStatus.detectedLoops}</ThemedText>
        </ThemedView>
        
        <ThemedView style={styles.statRow}>
          <ThemedText style={styles.statLabel}>最後の検出:</ThemedText>
          <ThemedText style={styles.statValue}>
            {serviceStatus.lastDetection || '未検出'}
          </ThemedText>
        </ThemedView>

        <TouchableOpacity 
          style={[styles.resetButton, { backgroundColor: Colors[colorScheme ?? 'light'].tint }]}
          onPress={resetStats}
        >
          <ThemedText style={styles.resetButtonText}>統計をリセット</ThemedText>
        </TouchableOpacity>
      </ThemedView>

      <ThemedView style={[styles.card, { backgroundColor: Colors[colorScheme ?? 'light'].background }]}>
        <ThemedText type="subtitle">使用方法</ThemedText>
        <ThemedText style={styles.instructionText}>
          1. 音声監視をオンにしてください{'\n'}
          2. アプリは背景で動作し、ネガティブな思考パターンを検出します{'\n'}
          3. 検出時にはバイブレーションでお知らせします{'\n'}
          4. すべての音声処理はデバイス内で完結し、外部に送信されません
        </ThemedText>
      </ThemedView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
  },
  header: {
    alignItems: 'center',
    marginBottom: 30,
    paddingTop: 20,
  },
  title: {
    fontSize: 32,
    fontWeight: 'bold',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    opacity: 0.7,
    textAlign: 'center',
  },
  card: {
    borderRadius: 12,
    padding: 20,
    marginBottom: 20,
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.1,
    shadowRadius: 3.84,
    elevation: 5,
  },
  serviceControl: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10,
  },
  statusText: {
    fontSize: 14,
    opacity: 0.8,
  },
  statsTitle: {
    marginBottom: 15,
  },
  statRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 10,
  },
  statLabel: {
    fontSize: 16,
  },
  statValue: {
    fontSize: 16,
    fontWeight: '600',
  },
  resetButton: {
    borderRadius: 8,
    padding: 12,
    alignItems: 'center',
    marginTop: 15,
  },
  resetButtonText: {
    color: 'white',
    fontWeight: '600',
  },
  instructionText: {
    fontSize: 14,
    lineHeight: 20,
    marginTop: 10,
    opacity: 0.8,
  },
});

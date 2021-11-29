import React, { useEffect, useState } from 'react';
import { StyleSheet, View, Text, Button, ScrollView } from 'react-native';
import Reader from 'react-native-acs';

function hexToHexString(hex: number[]): string {
  let chars = [];
  for (let i = 0; i < hex.length; i++) {
    chars.push(hex[i].toString(16).toUpperCase().padStart(2, '0'));
  }

  return chars.join(',0x');
}

export default function App() {
  const [result, setResult] = useState('');
  const [readerInfo, setReaderInfo] = useState('');
  const [uid, setUid] = useState('');

  useEffect(() => {
    const stateChangeListener = async (data: any) => {
      console.log(data);
      if (data.currState === 'Present' && data.prevState === 'Absent') {
        Reader.ConnectToCard(0)
          .then((atr) => {
            setResult(hexToHexString(atr));
            getuid();
          })
          .catch(console.log);
      }
    };

    Reader.addListener('onStateChange', stateChangeListener);

    return () => {
      Reader.removeListener('onStateChange', stateChangeListener);
    };
  }, []);

  const InitReader = async () => {
    Reader.Init()
      .then((info) => {
        console.log(info);
        setReaderInfo(info.readerName);
      })
      .catch(console.log);

    return null;
  };

  const getuid = async () => {
    let command = [0xff, 0xca, 0x00, 0x00, 0x00];
    let response = await Reader.Transmit(0, command);

    setUid(hexToHexString(response));
  };

  return (
    <ScrollView>
      <View style={styles.container}>
        <Button title="Test" onPress={InitReader} />
        <Text>Reader Info: {readerInfo}</Text>
        <Text>ATR: {result}</Text>
        <Text>UID: {uid}</Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  box: {
    width: 60,
    height: 60,
    marginVertical: 20,
  },
});

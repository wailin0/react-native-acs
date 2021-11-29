import React, {useEffect, useState} from 'react';
import { Blob,Buffer } from "buffer";
import { StyleSheet, View, Text, Button, ScrollView } from 'react-native';
import Reader from 'react-native-acs';


function hexStringToHex(hexStr: string): number[] {
  const bytes = [];
  for (let i = 0; i < hexStr.length; i += 2) {
    bytes.push(parseInt(hexStr.substr(i, 2), 16));
  }
  return bytes;
}

function hexToHexString(hex: number[]): string {
  let chars = [];
  for (let i = 0; i < hex.length; i++) {
    chars.push(hex[i].toString(16).toUpperCase().padStart(2,"0"));
  }

  return chars.join(",0x")
}

function hexToFile(hex = [], fileMime = 'image/jpeg', fileName = "foto.jpeg") {
  let buffer = new ArrayBuffer(hex.length)
  let bytes = new Uint8Array(buffer)

  hex.forEach((value, key) => {
      bytes[key] = value
  })
  
  
}

export default function App() {
  const [result, setResult] = React.useState("");
  const [readerInfo,setReaderInfo] = React.useState("");
  const [uid,setUid] = useState("");

  useEffect(() => {
    const stateChangeListener = async (data: any) => {
      console.log(data);
      if(data.currState == "Present" && data.prevState == "Absent"){
        Reader.ConnectToCard(0).then(atr => {
          setResult(hexToHexString(atr))
          getuid()
          getFoto();
        }).catch(console.log)
        // // setResult(hexToHexString(atr))
        
      }
    }

    Reader.addListener("onStateChange",stateChangeListener);

    return () => {
      Reader.removeListener("onStateChange",stateChangeListener);
    }
  },[]);

  const InitReader = async () => {
    Reader.Init().then(info => {
      console.log(info);
      setReaderInfo(info.readerName)
    }).catch(console.log);

    return null
  }

  const getuid = async () => {
    let command = [0xFF,0xCA,0x00,0x00,0x00];
    let response = await Reader.Transmit(0,command)

    setUid(hexToHexString(response));
  }

  const getFoto = async () => {
    let apduList = [
      [0x00, 0xA4, 0x00, 0x00, 0x02, 0x7F, 0x0A],
      [0x00, 0xA4, 0x00, 0x00, 0x02, 0x6F, 0xF2],
      [0x00, 0xB0, 0x00, 0x00, 0x02]
    ]

    let res = await Reader.Transmit(0,apduList[0]);
    console.log(res)

    res = await Reader.Transmit(0,apduList[1]);
    console.log(res)

    res = await Reader.Transmit(0,apduList[2]);
    console.log(res)

    let fileLen = (res[0] << 8) | res[1];

    let chunckSize = 253
    let batch = parseInt((fileLen / chunckSize).toString()) + (fileLen % chunckSize ? 1 : 0)
    let readed = [];
    let apdu = [];
    let p1 = 0;
    let p2 = 0;

    for (let i = 0; i < batch; i++) {
        p1 = readed.length >> 8
        p2 = readed.length & 0x00FF
        apdu = [0x00, 0xB0, p1, p2, 0xFF]
        
        res = await Reader.Transmit(0,apdu);

        let bytes = res.slice(0, res.length - 2) // Remove the last SW1 and SW2 bytes
        if (bytes.length < 1) {
            break
        }
        readed.push(...bytes);
    }

    let slicea = readed.slice(0, fileLen + 2)
  }

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

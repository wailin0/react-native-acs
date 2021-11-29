import { DeviceEventEmitter, NativeModules, Platform } from 'react-native';
import { EventEmitter } from 'events';

const LINKING_ERROR =
  `The package 'react-native-acs' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo managed workflow\n';

const ReaderModule = NativeModules.ReaderModule
  ? NativeModules.ReaderModule
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

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
    chars.push(hex[i].toString(16).toUpperCase().padStart(2, '0'));
  }

  return chars.join('');
}

interface ReaderInfoInterface {
  readerName: string;
  numSlots: number;
}

interface ReaderModulexInterface {
  Init(): Promise<ReaderInfoInterface>;
  ConnectToCard(slotNum: number): Promise<number[]>;
  Transmit(slotNum: number, command: number[]): Promise<number[]>;
}

class Reader extends EventEmitter implements ReaderModulexInterface {
  listener: any;

  constructor() {
    super();
    const stateEvent = 'onStateChange';
    this.listener = DeviceEventEmitter.addListener(stateEvent, (data) => {
      this.emit(stateEvent, data);
    });
  }

  async Init(): Promise<ReaderInfoInterface> {
    await ReaderModule.Init();

    return ReaderModule.GetReaderInfo();
  }

  async ConnectToCard(slotNum: number): Promise<number[]> {
    let atr = await ReaderModule.ConnectToCard(slotNum);

    return hexStringToHex(atr);
  }

  async Transmit(slotNum: number, command: number[]): Promise<number[]> {
    let request = hexToHexString(command);

    let response = await ReaderModule.Transmit(slotNum, request);

    return hexStringToHex(response);
  }
}

export default new Reader();

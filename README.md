# react-native-ldk

:warning: This is pre-alpha software and not suitable for production apps yet.


### Description
This library hopes to simplify the process of adding Lightning via LDK to any React-Native app.

## Getting started

```bash
yarn add @synonymdev/react-native-ldk
#or
npm i -S @synonymdev/react-native-ldk
````

### iOS installation
```bash
cd ios && pod install && cd ../
````

### Android installation
1. Add the following line to `dependencies` in `/android/app/build.gradle`
```
dependencies {
  ...
  implementation files("../../node_modules/@synonymdev/react-native-ldk/android/libs/LDK-release.aar")
}
```
2. Ensure `minSdkVersion` is set to at least `24` in `/android/build.gradle`


## Running example app
```bash

#Build dist files
git clone https://github.com/synonymdev/react-native-ldk.git
cd react-native-ldk/lib/ && yarn install && yarn build && cd ../

cd example/ && yarn install && yarn rn-setup

yarn ios
#or
yarn android
```

## Example Setup & Usage

To run the following example:
 1. Install & Run [Polar](https://github.com/jamaljsr/polar)
 2. Create a network and replace the `USER`, `PASS` & `HOST` variables with the correct info provided under the "Connect" tab for your Bitcoin instance in Polar.
 3. See [Notes](#notes) for additional instructions.
 
```javascript
import AsyncStorage from '@react-native-async-storage/async-storage';
import lm, { ENetworks } from '@synonymdev/react-native-ldk';
import { randomBytes } from 'react-native-randombytes';
import { encode as btoa } from 'js-base64';

const USER = 'polaruser';
const PASS = 'polarpass';
const HOST = 'http://127.0.0.1:18443/';

const bitcoinRPC = async (method, params) => {
    const data = { jsonrpc: '1.0', id: 'todo', method, params };
    const res = await fetch(HOST, {
        method: 'POST',
        headers: {
            Accept: 'application/json',
            'Content-Type': 'application/json',
            Authorization: `Basic ${btoa(`${USER}:${PASS}`)}`,
        },
        body: JSON.stringify(data),
    });
    const json = await res.json();
    if (json.error) {
        throw new Error(json.error);
    }
    return json.result;
};

export const getBlockHeaderHexFromHash = async (hash) => await bitcoinRPC('getblockheader', [hash, false]);
export const getBlockHeaderHashFromHeight = async (height) => await bitcoinRPC('getblockhash', [height]);
export const getBlockHeight = async () => {
	const { blocks } = await bitcoinRPC('getblockchaininfo', []);
	return Number(blocks);
};
const getTxData = async (txid) => await bitcoinRPC('getrawtransaction', [txid, true]);

const account = {
	name: 'wallet0',
	seed: randomBytes(32).toString('hex'),
}
const network = ENetworks.testnet;
const genesisHash = await getBlockHeaderHashFromHeight(0);
const setItem = async (key, value) => await AsyncStorage.setItem(key, value);
const getItem = async (key) => await AsyncStorage.getItem(key);
const getBestBlock = async () => {
	const height = await getBlockHeight();
	const hash = await getBlockHeaderHashFromHeight(height);
	const hex = await getBlockHeaderHexFromHash(hash);
	return { height, hash, hex };
};
const getTransactionData = async (txid) => {
	const txData = await getTxData(txid);
	const currentBlockHeight = await getBlockHeight();
	const height = txData.confirmations ? currentBlockHeight - txData.confirmations : 0;
	const header = txData.blockhash ? await getBlockHeaderHexFromHash(txData.blockhash) : '';
	const transaction = txData.hex;
	return { header, height, transaction };
};

const startResponse = await lm.start({
	account,
	network,
	genesisHash,
	getItem,
	setItem,
	getBestBlock,
	getTransactionData,
});
if (startResponse.isErr()) {
	//LDK failed to start
    console.error(startResponse.error);
	return;
} else {
	// Sync when new blocks are detected.
	setInterval(async () => {
		await lm.syncLdk();
	}, 5000);
}

//Subscribe to LDK logs
const logListener = ldk.addLogListener((message) => {
    console.log(message);
});

//Backup LDK account.
const backupResponse = await lm.backupAccount({
	account,
	setItem,
	getItem,
});
if (backupResponse.isErr()) {
	console.log(backupResponse.error.message);
	return;
}
//Store this backup string somewhere safe.
console.log(backupResponse.value);

//Unsubscribe if listening component is unmounted
ldk.removeLogListener(logListener);
```

### Importing an account from a backup:
```javascript
//The backup string can be acquired from the `lm.backupAccount` method.
const backup = backupResponse.value;
//Import LDK account backup.
const importResponse = await lm.importAccount({
	backup,
	setItem,
	getItem,
	overwrite: true,
});
if (importResponse.isErr()) {
	console.log(importResponse.error.message);
	return;
}
//Successfully imported account. 
//This value can now be used as the `account` param in `lm.start`
console.log(importResponse.value);
```

## Notes
 - It is important to not mix and match account names and seeds when starting LDK. Doing so can result in a corrupt save.

## Upgrading LDK
- Use latest LDK-release.aar from [ldk-garbagecollected](https://github.com/lightningdevkit/ldk-garbagecollected/releases) and place in `lib/android/libs`.
- Use latest LDKFramework.xcframework from [ldk-swift](https://github.com/lightningdevkit/ldk-swift/releases) and place in lib/ios.
  - To get `pod install` working you might have to open the `LDKFramework.xcframework` directory, delete non ios frameworks and remove all references to deleted frameworks inside `LDKFramework.xcframework/Info.plist`.
- Update Swift and Kotlin code if there are any breaking changes.

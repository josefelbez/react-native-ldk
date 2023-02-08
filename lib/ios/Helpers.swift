//
//  Helpers.swift
//  react-native-ldk
//
//  Created by Jason van den Berg on 2022/05/10.
//

import Foundation
import LightningDevKit

func handleResolve(_ resolve: RCTPromiseResolveBlock, _ res: LdkCallbackResponses) {
    if res != .log_write_success {
        LdkEventEmitter.shared.send(withEvent: .native_log, body: "Success: \(res.rawValue)")
    }
    resolve(res.rawValue)
}

func handleReject(_ reject: RCTPromiseRejectBlock, _ ldkError: LdkErrors, _ error: Error? = nil, _ message: String? = nil) {
    if let error = error as? NSError {
        LdkEventEmitter.shared.send(withEvent: .native_log, body: "Error: \(error.localizedDescription). Message: '\(message ?? "")'")
        reject(ldkError.rawValue, message ?? error.localizedDescription, error)
        return
    }
    
    LdkEventEmitter.shared.send(withEvent: .native_log, body: "Error: \(ldkError.rawValue). Message: '\(message ?? "")'")
    reject(ldkError.rawValue, message ?? ldkError.rawValue, NSError(domain: ldkError.rawValue, code: ldkError.hashValue))
}

func currencyString(_ currency: Currency) -> String {
    switch currency {
    case .Bitcoin:
        return "Bitcoin"
    case .BitcoinTestnet:
        return "BitcoinTestnet"
    case .Regtest:
        return "Regtest"
    case .Simnet:
        return "Simnet"
    case .Signet:
        return "Signet"
    @unknown default:
        return "Unknown"
    }
}

extension Invoice {
    var asJson: [String: Any?] {
        //Break down to get the decription. Will crash if all on a single line.
        let signedRawInvoice = intoSignedRaw()
        let rawInvoice = signedRawInvoice.rawInvoice()
        let description = rawInvoice.description()
        let descriptionString = description?.intoInner() ?? ""
        
        return [
            "amount_satoshis": amountMilliSatoshis() != nil ? amountMilliSatoshis()! / 1000 : nil,
            "description": descriptionString,
            "check_signature": checkSignature().isOk(),
            "is_expired": isExpired(),
            "duration_since_epoch": durationSinceEpoch(),
            "expiry_time": expiryTime(),
            "min_final_cltv_expiry": minFinalCltvExpiry(),
            "payee_pub_key": Data(payeePubKey() ?? []).hexEncodedString(),
            "recover_payee_pub_key": Data(recoverPayeePubKey()).hexEncodedString(),
            "payment_hash": Data(paymentHash() ?? []).hexEncodedString(),
            "payment_secret": Data(paymentSecret() ?? []).hexEncodedString(),
            "timestamp": timestamp(),
            "features": Data(features()?.write() ?? []).hexEncodedString(),
            "currency": currencyString(currency()),
            "to_str": toStr(),
            "route_hints": routeHints().map({ $0.getA().map({ $0.asJson }) }),
        ]
    }
}

extension RouteHintHop {
    var asJson: [String: Any] {
        return [
            "src_node_id": Data(getSrcNodeId()).hexEncodedString(),
            "short_channel_id": String(getShortChannelId())
        ]
    }
}

//Our own channels
extension ChannelDetails {
    var asJson: [String: Any] {
        let shortChannelId = getShortChannelId()
        return [
            "channel_id": Data(getChannelId() ?? []).hexEncodedString(),
            "is_public": getIsPublic(),
            "is_usable": getIsUsable(),
            "is_channel_ready": getIsChannelReady(),
            "is_outbound": getIsOutbound(),
            "balance_sat": getBalanceMsat() / 1000,
            "counterparty_node_id": Data(getCounterparty().getNodeId()).hexEncodedString(),
            "funding_txid": Data(getFundingTxo()?.getTxid()?.reversed() ?? []).hexEncodedString(),
            "channel_type": Data(getChannelType()?.write() ?? []).hexEncodedString(),
            "user_channel_id": Data(getUserChannelId()).hexEncodedString(), //Sting
            "confirmations_required": getConfirmationsRequired() as Any, // Optional number
            "short_channel_id": shortChannelId != nil ? String(shortChannelId!) : "",
            "inbound_scid_alias": getInboundScidAlias() as Any, //Optional number
            "inbound_payment_scid": getInboundPaymentScid() as Any, //Optional number,
            "inbound_capacity_sat": getInboundCapacityMsat() / 1000,
            "outbound_capacity_sat": getOutboundCapacityMsat() / 1000,
            "channel_value_satoshis": getChannelValueSatoshis(),
            "force_close_spend_delay": getForceCloseSpendDelay() as Any, //Optional number
            "unspendable_punishment_reserve": getUnspendablePunishmentReserve() as Any, //Optional number
            "config_forwarding_fee_base_msat": getConfig()?.getForwardingFeeBaseMsat() ?? 0 / 1000, //Optional number
            "config_forwarding_fee_proportional_millionths": getConfig()?.getForwardingFeeProportionalMillionths() ?? 0 / 1000 //Optional number
        ]
    }
}

//Channels in our network graph
extension ChannelInfo {
    var asJson: [String: Any] {
        return [
            "capacity_sats": getCapacitySats() as Any, //Optional number
            "node_one": Data(getNodeOne().asSlice()).hexEncodedString(), //String
            "node_two": Data(getNodeTwo().asSlice()).hexEncodedString(), //String
            
            "one_to_two_fees_base_sats": getOneToTwo()?.getFees().getBaseMsat() ?? 0 / 1000, //Number
            "one_to_two_fees_proportional_millionths": getOneToTwo()?.getFees().getProportionalMillionths() ?? 0, //Number
            "one_to_two_enabled": getOneToTwo()?.getEnabled() ?? false, //Bool
            "one_to_two_last_update": getOneToTwo()?.getLastUpdate() ?? 0, //Number
            "one_to_two_htlc_maximum_sats": getOneToTwo()?.getHtlcMaximumMsat() ?? 0 / 1000, //Number
            "one_to_two_htlc_minimum_sats": getOneToTwo()?.getHtlcMinimumMsat() ?? 0 / 1000, //Number

            "two_to_one_fees_base_sats": getTwoToOne()?.getFees().getBaseMsat() ?? 0 / 1000, //Number
            "two_to_one_fees_proportional_millionths": getTwoToOne()?.getFees().getProportionalMillionths() ?? 0, //Number
            "two_to_one_enabled": getTwoToOne()?.getEnabled() ?? false, //Bool
            "two_to_one_last_update": getTwoToOne()?.getLastUpdate() ?? 0, //Number
            "two_to_one_htlc_maximum_sats": getTwoToOne()?.getHtlcMaximumMsat() ?? 0 / 1000, //Number
            "two_to_one_htlc_minimum_sats": getTwoToOne()?.getHtlcMinimumMsat() ?? 0 / 1000, //Number
        ]
    }
}

//Nodes in our network graph
extension NodeInfo {
    var asJson: [String: Any] {
        return [
            "shortChannelIds": getChannels().map({ String($0) }),
            "lowest_inbound_channel_fees_base_sat": getLowestInboundChannelFees()?.getBaseMsat() ?? 0 / 1000,
            "lowest_inbound_channel_fees_proportional_millionths": getLowestInboundChannelFees()?.getProportionalMillionths() ?? 0,
            "announcement_info_last_update": Int(getAnnouncementInfo()?.getLastUpdate() ?? 0) * 1000
        ]
    }
}

extension LightningDevKit.RouteHop {
    var asJson: [String: Any] {
        return [
            "pubkey": Data(getPubkey()).hexEncodedString(),
            "fee_sat": getFeeMsat() / 1000,
            "short_channel_id": String(getShortChannelId()),
            "cltv_expiry_delta": getCltvExpiryDelta()
        ]
    }
}

extension Data {
    struct HexEncodingOptions: OptionSet {
        let rawValue: Int
        static let upperCase = HexEncodingOptions(rawValue: 1 << 0)
    }

    func hexEncodedString(options: HexEncodingOptions = []) -> String {
        let format = options.contains(.upperCase) ? "%02hhX" : "%02hhx"
        return map { String(format: format, $0) }.joined()
    }
}

extension StringProtocol {
    var hexaData: Data { .init(hexa) }
    var hexaBytes: [UInt8] { .init(hexa) }
    private var hexa: UnfoldSequence<UInt8, Index> {
        sequence(state: startIndex) { startIndex in
            guard startIndex < endIndex else { return nil }
            let endIndex = index(startIndex, offsetBy: 2, limitedBy: endIndex) ?? endIndex
            defer { startIndex = endIndex }
            return UInt8(self[startIndex..<endIndex], radix: 16)
        }
    }
}

//MARK: Module can be initialised on main thread as LDK handles all it's own tasks on background threads  (https://reactnative.dev/docs/native-modules-ios#implementing--requiresmainqueuesetup)
extension Ldk {
    @objc
    static func requiresMainQueueSetup() -> Bool {
        return true
    }
}

extension LdkEventEmitter {
    @objc
    override static func requiresMainQueueSetup() -> Bool {
        return true
    }
}

extension String: Error {}

extension URL {
    func downloadTask(destination: URL, completion: @escaping (Error?) -> Void) -> URLSessionDataTask? {
        if FileManager().fileExists(atPath: destination.path) {
            completion(NSError(domain: "", code: 500, userInfo: [ NSLocalizedDescriptionKey: "File already exists"]))
            return nil
        }
      
        let session = URLSession(configuration: URLSessionConfiguration.default, delegate: nil, delegateQueue: nil)
        var request = URLRequest(url: self)
        request.httpMethod = "GET"
        return session.dataTask(with: request, completionHandler: { data, response, error in
            guard error == nil else {
                return completion(error)
            }
            
            guard let response = response as? HTTPURLResponse, let data = data else {
                return completion(NSError(domain: "", code: 500, userInfo: [ NSLocalizedDescriptionKey: "Failed to get HTTP response"]))
            }
            
            guard response.statusCode == 200 else {
                return completion(NSError(domain: "", code: response.statusCode, userInfo: [ NSLocalizedDescriptionKey: "Download failed from \(self) with status (\(response.statusCode))"]))
            }
            
            do {
                try data.write(to: destination, options: Data.WritingOptions.atomic)
                return completion(nil)
            } catch {
               return completion(error)
            }
        })
    }
}

extension RapidGossipSync {
    func downloadAndUpdateGraph(downloadUrl: String, tempStoragePath: URL, timestamp: UInt32, completion: @escaping (Error?) -> Void) {
        let destinationFile = tempStoragePath.appendingPathComponent("\(timestamp).bin")
      
        //Cleanup old one
        if FileManager().fileExists(atPath: destinationFile.path) {
            try? FileManager().removeItem(atPath: destinationFile.path)
        }

        let url = URL(string: "\(downloadUrl)\(timestamp)")!

        let task = url.downloadTask(destination: destinationFile) { error in
            if let error = error {
                return completion(error)
            }
                        
            let res = self.updateNetworkGraph(updateData: [UInt8](try! Data(contentsOf: destinationFile)))
            guard res.isOk() else {
                var errorMessage = "Failed to update network graph."
                switch res.getError()?.getValueType() {
                case .LightningError:
                    errorMessage = "Rapid sync error. \(res.getError()!.getValueAsLightningError()!.getErr())" //Couldn't find channel for update.
                    break;
                case .DecodeError:
                    errorMessage = "Rapid sync error. IO error: \(res.getError()!.getValueAsDecodeError()?.getValueType())"
                    break;
                default:
                    errorMessage = "Unknown rapid sync error."
                    break;
                }

                completion(NSError(domain: "", code: 500, userInfo: [ NSLocalizedDescriptionKey: errorMessage]))
                try? FileManager().removeItem(atPath: destinationFile.path)
                return
            }
            
            completion(nil)
            try? FileManager().removeItem(atPath: destinationFile.path)
        }
        
        task?.resume()
    }
}

func handlePaymentSendFailure(_ reject: RCTPromiseRejectBlock, error: Bindings.PaymentSendFailure) {
    switch error.getValueType() {
    case .AllFailedResendSafe:
//            let errorMessage = ""
//            error.getValueAsAllFailedRetrySafe()?.forEach({ apiError in
//                apiError.getValueType() //TODO iterate through all
//            })

        return handleReject(reject, .invoice_payment_fail_resend_safe, nil, error.getValueAsAllFailedResendSafe().map { $0.description } )
    case .ParameterError:
        guard let parameterError = error.getValueAsParameterError() else {
            return handleReject(reject, .invoice_payment_fail_parameter_error)
        }

        let parameterErrorType = parameterError.getValueType()

        switch parameterErrorType {
        case .APIMisuseError:
            return handleReject(reject, .invoice_payment_fail_parameter_error, nil, "parameterError.getValueType().debugDescription")
        case .FeeRateTooHigh:
            return handleReject(reject, .invoice_payment_fail_parameter_error, nil, parameterError.getValueAsFeeRateTooHigh()?.getErr())
        case .InvalidRoute:
            return handleReject(reject, .invoice_payment_fail_parameter_error, nil, parameterError.getValueAsInvalidRoute()?.getErr())
        case .ChannelUnavailable:
            return handleReject(reject, .invoice_payment_fail_parameter_error, nil, parameterError.getValueAsChannelUnavailable()?.getErr())
        case .IncompatibleShutdownScript:
            return handleReject(reject, .invoice_payment_fail_parameter_error, nil, "IncompatibleShutdownScript")
        case .MonitorUpdateInProgress:
            return handleReject(reject, .invoice_payment_fail_parameter_error, nil, "MonitorUpdateInProgress")
        @unknown default:
            return handleReject(reject, .invoice_payment_fail_parameter_error, nil, error.getValueAsParameterError().debugDescription)
        }
    case .PartialFailure:
        return handleReject(reject, .invoice_payment_fail_partial, nil, error.getValueAsPartialFailure().debugDescription)
    case .PathParameterError:
        return handleReject(reject, .invoice_payment_fail_path_parameter_error, nil, error.getValueAsPartialFailure().debugDescription)
    default:
        return handleReject(reject, .invoice_payment_fail_sending)
    }
}

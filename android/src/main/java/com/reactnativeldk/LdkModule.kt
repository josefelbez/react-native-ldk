package com.reactnativeldk

import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter
import com.reactnativeldk.classes.*
import org.json.JSONObject
import org.ldk.batteries.ChannelManagerConstructor
import org.ldk.batteries.NioPeerHandler
import org.ldk.enums.Currency
import org.ldk.enums.Network
import org.ldk.impl.bindings.get_ldk_c_bindings_version
import org.ldk.impl.bindings.get_ldk_version
import org.ldk.structs.*
import org.ldk.structs.Result_InvoiceParseOrSemanticErrorZ.Result_InvoiceParseOrSemanticErrorZ_OK
import org.ldk.structs.Result_InvoiceSignOrCreationErrorZ.Result_InvoiceSignOrCreationErrorZ_OK
import java.net.InetSocketAddress


//MARK: ************Replicate in typescript and swift************
enum class EventTypes {
    ldk_log,
    swift_log,
    register_tx,
    register_output,
    broadcast_transaction,
    persist_manager,
    persist_new_channel,
    persist_graph,
    update_persisted_channel,
    channel_manager_funding_generation_ready,
    channel_manager_payment_received,
    channel_manager_payment_sent,
    channel_manager_open_channel_request,
    channel_manager_payment_path_successful,
    channel_manager_payment_path_failed,
    channel_manager_payment_failed,
    channel_manager_spendable_outputs,
    channel_manager_channel_closed,
    channel_manager_discard_funding
}
//*****************************************************************

enum class LdkErrors {
    unknown_error,
    already_init,
    invalid_seed_hex,
    init_chain_monitor,
    init_keys_manager,
    init_user_config,
    init_peer_manager,
    invalid_network,
    load_channel_monitors,
    init_channel_monitor,
    init_network_graph,
    init_peer_handler,
    add_peer_fail,
    init_channel_manager,
    decode_invoice_fail,
    init_invoice_payer,
    invoice_payment_fail,
    init_ldk_currency,
    invoice_create_failed
}

enum class LdkCallbackResponses {
    fees_updated,
    log_level_updated,
    chain_monitor_init_success,
    keys_manager_init_success,
    channel_manager_init_success,
    load_channel_monitors_success,
    config_init_success,
    net_graph_msg_handler_init_success,
    chain_monitor_updated,
    network_graph_init_success,
    add_peer_success,
    chain_sync_success,
    invoice_payment_success,
    tx_set_confirmed,
    tx_set_unconfirmed
}

class LdkModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    init {
        LdkEventEmitter.setContext(reactContext)
    }

    override fun getName(): String {
        return "Ldk"
    }

    //Zero config objects lazy loaded into memory when required
    private val feeEstimator: LdkFeeEstimator by lazy { LdkFeeEstimator() }
    private val logger: LdkLogger by lazy { LdkLogger() }
    private val broadcaster: LdkBroadcaster by lazy { LdkBroadcaster() }
    private val persister: LdkPersister by lazy { LdkPersister() }
    private val filter: LdkFilter by lazy { LdkFilter() }
    private val channelManagerPersister: LdkChannelManagerPersister by lazy { LdkChannelManagerPersister() }
    private val scorer: MultiThreadedLockableScore by lazy { MultiThreadedLockableScore.of(Scorer.with_default().as_Score()) }

    //Config required to setup below objects
    private var chainMonitor: ChainMonitor? = null
    private var keysManager: KeysManager? = null
    private var channelManager: ChannelManager? = null
    private var userConfig: UserConfig? = null
    private var channelMonitors: Array<ByteArray>? = null //TODO don't keep this, just add it from initChannelManager
    private var networkGraph: NetworkGraph? = null
    private var peerManager: PeerManager? = null
    private var peerHandler: NioPeerHandler? = null
    private var channelManagerConstructor: ChannelManagerConstructor? = null
    private var invoicePayer: InvoicePayer? = null
    private var ldkNetwork: Network? = null
    private var ldkCurrency: Currency? = null

    //Startup methods
    @ReactMethod
    fun initChainMonitor(promise: Promise) {
        if (chainMonitor !== null) {
            return handleReject(promise, LdkErrors.already_init)
        }

        chainMonitor = ChainMonitor.of(
            Option_FilterZ.some(filter.filter),
            broadcaster.broadcaster,
            logger.logger,
            feeEstimator.feeEstimator,
            persister.persister
        )

        handleResolve(promise, LdkCallbackResponses.chain_monitor_init_success)
    }

    @ReactMethod
    fun initKeysManager(seed: String, promise: Promise) {
        if (keysManager !== null) {
            return handleReject(promise, LdkErrors.already_init)
        }

        val nanoSeconds = System.currentTimeMillis() * 1000
        val seconds = nanoSeconds / 1000 / 1000
        val seedBytes = seed.hexa()

        if (seedBytes.count() != 32) {
            return handleReject(promise, LdkErrors.invalid_seed_hex)
        }

        keysManager = KeysManager.of(seedBytes, seconds, nanoSeconds.toInt())

        handleResolve(promise, LdkCallbackResponses.keys_manager_init_success)
    }

    @ReactMethod
    fun loadChannelMonitors(channelMonitorStrings: ReadableArray, promise: Promise) {
        //TODO remove this in favour of passing channel monitors through in initChannelManager
        handleResolve(promise, LdkCallbackResponses.load_channel_monitors_success)
    }

    @ReactMethod
    fun initConfig(acceptInboundChannels: Boolean, manuallyAcceptInboundChannels: Boolean, announcedChannels: Boolean, minChannelHandshakeDepth: Double, promise: Promise) {
        if (userConfig !== null) {
            return handleReject(promise, LdkErrors.already_init)
        }

        userConfig = UserConfig.with_default()
        userConfig!!._accept_inbound_channels = acceptInboundChannels
        userConfig!!._manually_accept_inbound_channels = manuallyAcceptInboundChannels

        val newChannelConfig = ChannelConfig.with_default()
        newChannelConfig._announced_channel = announcedChannels

        val channelHandshake = ChannelHandshakeConfig.with_default()
        channelHandshake._minimum_depth = minChannelHandshakeDepth.toInt()
        userConfig!!._own_channel_config = channelHandshake

        val channelHandshakeLimits = ChannelHandshakeLimits.with_default()
        channelHandshakeLimits._force_announced_channel_preference = announcedChannels
        userConfig!!._peer_channel_config_limits = channelHandshakeLimits

        handleResolve(promise, LdkCallbackResponses.config_init_success)
    }

    @ReactMethod
    fun initNetworkGraph(genesisHash: String, promise: Promise) {
        if (networkGraph !== null) {
            return handleReject(promise, LdkErrors.already_init)
        }

        networkGraph = NetworkGraph.of(genesisHash.hexa())
        handleResolve(promise, LdkCallbackResponses.network_graph_init_success)
    }

    @ReactMethod
    fun initChannelManager(network: String, serializedChannelManager: String, blockHash: String, blockHeight: Double, promise: Promise) {
        if (channelManager !== null) {
            return handleReject(promise, LdkErrors.already_init)
        }

        chainMonitor ?: return handleReject(promise, LdkErrors.init_chain_monitor)
        keysManager ?: return handleReject(promise, LdkErrors.init_keys_manager)
        userConfig ?: return handleReject(promise, LdkErrors.init_user_config)
//        channelMonitors ?: return handleReject(promise, LdkErrors.load_channel_monitors) //TODO remove when passed into this function
        networkGraph ?: return handleReject(promise, LdkErrors.init_network_graph)

        when (network) {
            "regtest" -> {
                ldkNetwork = Network.LDKNetwork_Regtest
                ldkCurrency = Currency.LDKCurrency_Regtest
            }
            "testnet" -> {
                ldkNetwork = Network.LDKNetwork_Testnet
                ldkCurrency = Currency.LDKCurrency_BitcoinTestnet
            }
            "mainnet" -> {
                ldkNetwork = Network.LDKNetwork_Bitcoin
                ldkCurrency = Currency.LDKCurrency_Bitcoin
            }
            else -> { // Note the block
                return handleReject(promise, LdkErrors.invalid_network)
            }
        }

        try {
            if (channelMonitors == null || channelMonitors!!.isEmpty()) {
                channelManagerConstructor = ChannelManagerConstructor(
                    ldkNetwork,
                    userConfig,
                    blockHash.hexa(),
                    blockHeight.toInt(),
                    keysManager!!.as_KeysInterface(),
                    feeEstimator.feeEstimator,
                    chainMonitor,
                    networkGraph,
                    broadcaster.broadcaster,
                    logger.logger
                )
            } else {
                println("Untested node restore")
                channelManagerConstructor = ChannelManagerConstructor(
                    serializedChannelManager.hexa(),
                    channelMonitors,
                    userConfig,
                    keysManager!!.as_KeysInterface(),
                    feeEstimator.feeEstimator,
                    chainMonitor,
                    filter.filter,
                    networkGraph!!.write(),
                    broadcaster.broadcaster,
                    logger.logger
                )
            }
        } catch (e: Exception) {
            return handleReject(promise, LdkErrors.unknown_error, Error(e))
        }

        channelManager = channelManagerConstructor!!.channel_manager
        this.networkGraph = channelManagerConstructor!!.net_graph

        channelManagerConstructor!!.chain_sync_completed(channelManagerPersister.channelManagerPersister, scorer)
        peerManager = channelManagerConstructor!!.peer_manager

        peerHandler = channelManagerConstructor!!.nio_peer_handler
        invoicePayer = channelManagerConstructor!!.payer

        handleResolve(promise, LdkCallbackResponses.channel_manager_init_success)
    }

    @ReactMethod
    fun syncChainMonitorWithChannelMonitor(blockHash: String, blockHeight: Double, promise: Promise) {
        //TODO
        handleResolve(promise, LdkCallbackResponses.chain_monitor_updated)
    }

    //MARK: Update methods

    @ReactMethod
    fun updateFees(high: Double, normal: Double, low: Double, promise: Promise) {
        feeEstimator.update(high.toInt(), normal.toInt(), low.toInt())
        handleResolve(promise, LdkCallbackResponses.fees_updated)
    }

    @ReactMethod
    fun setLogLevel(level: Double, active: Boolean, promise: Promise) {
        logger.setLevel(level.toInt(), active)
        handleResolve(promise, LdkCallbackResponses.log_level_updated)
    }

    @ReactMethod
    fun syncToTip(header: String, height: Double, promise: Promise) {
        channelManager ?: return handleReject(promise, LdkErrors.init_channel_manager)
        chainMonitor ?: return handleReject(promise, LdkErrors.init_chain_monitor)

        try {
            channelManager!!.as_Confirm().best_block_updated(header.hexa(), height.toInt())
            chainMonitor!!.as_Confirm().best_block_updated(header.hexa(), height.toInt())
        } catch (e: Exception) {
            return handleReject(promise, LdkErrors.unknown_error, Error(e))
        }

        handleResolve(promise, LdkCallbackResponses.chain_sync_success)
    }

    @ReactMethod
    fun addPeer(address: String, port: Double, pubKey: String, timeout: Double, promise: Promise) {
        peerHandler ?: return handleReject(promise, LdkErrors.init_peer_handler)

        try {
            peerHandler!!.connect(pubKey.hexa(), InetSocketAddress(address, port.toInt()), timeout.toInt())
        } catch (e: Exception) {
            return handleReject(promise, LdkErrors.add_peer_fail, Error(e))
        }

        handleResolve(promise, LdkCallbackResponses.add_peer_success)
    }

    @ReactMethod
    fun setTxConfirmed(header: String, transaction: String, pos: Double, height: Double, promise: Promise) {
        channelManager ?: return handleReject(promise, LdkErrors.init_channel_manager)
        chainMonitor ?: return handleReject(promise, LdkErrors.init_chain_monitor)

        val txData = arrayOf(TwoTuple_usizeTransactionZ.of(pos.toLong(), transaction.hexa()))

        channelManager!!.as_Confirm().transactions_confirmed(header.hexa(), txData, height.toInt())
        chainMonitor!!.as_Confirm().transactions_confirmed(header.hexa(), txData, height.toInt())

        handleResolve(promise, LdkCallbackResponses.tx_set_confirmed)
    }

    @ReactMethod
    fun setTxUnconfirmed(txId: String, promise: Promise) {
        channelManager ?: return handleReject(promise, LdkErrors.init_channel_manager)
        chainMonitor ?: return handleReject(promise, LdkErrors.init_chain_monitor)

        channelManager!!.as_Confirm().transaction_unconfirmed(txId.hexa())
        chainMonitor!!.as_Confirm().transaction_unconfirmed(txId.hexa())

        handleResolve(promise, LdkCallbackResponses.tx_set_unconfirmed)
    }

    //MARK: Payments
    @ReactMethod
    fun decode(paymentRequest: String, promise: Promise) {
        val parsed = Invoice.from_str(paymentRequest)
        if (!parsed.is_ok) {
            return handleReject(promise, LdkErrors.decode_invoice_fail)
        }

        val parsedInvoice = parsed as Result_InvoiceParseOrSemanticErrorZ_OK

        promise.resolve(parsedInvoice.res.asJson)
    }

    @ReactMethod
    fun pay(paymentRequest: String, promise: Promise) {
        invoicePayer ?: return handleReject(promise, LdkErrors.init_invoice_payer)

        val invoice = Invoice.from_str(paymentRequest)
        if (!invoice.is_ok) {
            return handleReject(promise, LdkErrors.decode_invoice_fail)
        }

        val res = invoicePayer!!.pay_invoice((invoice as Result_InvoiceParseOrSemanticErrorZ_OK).res)

        if (res.is_ok) {
            handleResolve(promise, LdkCallbackResponses.invoice_payment_success)
        }

        val error = res as Result_PaymentIdPaymentErrorZ.Result_PaymentIdPaymentErrorZ_Err // ?: return return handleReject(promise, LdkErrors.invoice_payment_fail)

        //TODO check payment fail options like in swift
        handleReject(promise, LdkErrors.invoice_payment_fail, Error(error.err.toString()))
    }

    @ReactMethod
    fun createPaymentRequest(amountSats: Double, description: String, promise: Promise) {
        channelManager ?: return handleReject(promise, LdkErrors.init_channel_manager)
        keysManager ?: return handleReject(promise, LdkErrors.init_keys_manager)
        ldkCurrency ?: return handleReject(promise, LdkErrors.init_ldk_currency)

        val res = UtilMethods.create_invoice_from_channelmanager(
            channelManager,
            keysManager!!.as_KeysInterface(),
            Currency.LDKCurrency_Bitcoin,
            Option_u64Z.some(amountSats.toLong()),
            description
        );

        if (res.is_ok) {
            return promise.resolve((res as Result_InvoiceSignOrCreationErrorZ_OK).res.asJson)
        }

        val error = res as Result_InvoiceSignOrCreationErrorZ
        return handleReject(promise, LdkErrors.invoice_create_failed, Error(error.toString()))
    }

    //MARK: Fetch methods

    @ReactMethod
    fun version(promise: Promise) {
        val res = JSONObject()
        res.put("c_bindings", get_ldk_c_bindings_version())
        res.put("ldk", get_ldk_version())
        promise.resolve(res.toString())
    }

    @ReactMethod
    fun nodeId(promise: Promise) {
        channelManager ?: return handleReject(promise, LdkErrors.init_channel_manager)

        promise.resolve(channelManager!!._our_node_id.hexEncodedString())
    }

    @ReactMethod
    fun listPeers(promise: Promise) {
        peerManager ?: return handleReject(promise, LdkErrors.init_peer_manager)

        val res = Arguments.createArray()
        val list = peerManager!!._peer_node_ids
        list.iterator().forEach {
            res.pushString(it.hexEncodedString())
        }

        promise.resolve(res)
    }

    @ReactMethod
    fun listChannels(promise: Promise) {
        channelManager ?: return handleReject(promise, LdkErrors.init_channel_manager)

        val list = Arguments.createArray()
        channelManager!!.list_channels().iterator().forEach { list.pushMap(it.asJson) }

        promise.resolve(list)
    }

    @ReactMethod
    fun listUsableChannels(promise: Promise) {
        channelManager ?: return handleReject(promise, LdkErrors.init_channel_manager)

        val list = Arguments.createArray()
        channelManager!!.list_usable_channels().iterator().forEach { list.pushMap(it.asJson) }

        promise.resolve(list)
    }
}

object LdkEventEmitter {
    private var reactContext: ReactContext? = null

    fun setContext(reactContext: ReactContext) {
        this.reactContext = reactContext
    }

    fun send(eventType: EventTypes, body: Any) {
        if (this.reactContext === null) {
            return
        }

        this.reactContext!!.getJSModule(RCTDeviceEventEmitter::class.java).emit(eventType.toString(), body)
    }
}
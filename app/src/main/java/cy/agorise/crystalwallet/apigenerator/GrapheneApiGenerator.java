package cy.agorise.crystalwallet.apigenerator;

import android.content.Context;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import cy.agorise.crystalwallet.dao.BitsharesAssetDao;
import cy.agorise.crystalwallet.dao.CryptoCoinBalanceDao;
import cy.agorise.crystalwallet.dao.CryptoCurrencyDao;
import cy.agorise.crystalwallet.dao.CrystalDatabase;
import cy.agorise.crystalwallet.manager.BitsharesAccountManager;
import cy.agorise.crystalwallet.models.BitsharesAsset;
import cy.agorise.crystalwallet.models.BitsharesAssetInfo;
import cy.agorise.crystalwallet.models.CryptoCoinBalance;
import cy.agorise.crystalwallet.models.CryptoCurrency;
import cy.agorise.crystalwallet.network.WebSocketThread;
import cy.agorise.graphenej.Address;
import cy.agorise.graphenej.Asset;
import cy.agorise.graphenej.AssetAmount;
import cy.agorise.graphenej.Converter;
import cy.agorise.graphenej.LimitOrder;
import cy.agorise.graphenej.ObjectType;
import cy.agorise.graphenej.Transaction;
import cy.agorise.graphenej.UserAccount;
import cy.agorise.graphenej.api.GetAccountBalances;
import cy.agorise.graphenej.api.GetAccountByName;
import cy.agorise.graphenej.api.GetAccounts;
import cy.agorise.graphenej.api.GetBlockHeader;
import cy.agorise.graphenej.api.GetKeyReferences;
import cy.agorise.graphenej.api.GetLimitOrders;
import cy.agorise.graphenej.api.GetMarketHistory;
import cy.agorise.graphenej.api.GetRelativeAccountHistory;
import cy.agorise.graphenej.api.ListAssets;
import cy.agorise.graphenej.api.LookupAssetSymbols;
import cy.agorise.graphenej.api.SubscriptionMessagesHub;
import cy.agorise.graphenej.api.TransactionBroadcastSequence;
import cy.agorise.graphenej.interfaces.NodeErrorListener;
import cy.agorise.graphenej.interfaces.SubscriptionListener;
import cy.agorise.graphenej.interfaces.WitnessResponseListener;
import cy.agorise.graphenej.models.AccountBalanceUpdate;
import cy.agorise.graphenej.models.AccountProperties;
import cy.agorise.graphenej.models.BaseResponse;
import cy.agorise.graphenej.models.HistoricalTransfer;
import cy.agorise.graphenej.models.SubscriptionResponse;
import cy.agorise.graphenej.models.WitnessResponse;


/**
 * This class manage all the api request directly to the Graphene Servers
 * Created by henry on 26/9/2017.
 */

public abstract class GrapheneApiGenerator {

    //TODO network connections
    //TODO make to work with all Graphene stype, not only bitshares
    private static String url = "http://128.0.69.157:8090";

    // The meesage broker for bitshares
    private static SubscriptionMessagesHub bitsharesSubscriptionHub = new SubscriptionMessagesHub("", "", true, new NodeErrorListener() {
        @Override
        public void onError(BaseResponse.Error error) {
            //TODO subcription hub error
        }
    });

    private static WebSocketThread subscriptionThread = new WebSocketThread(bitsharesSubscriptionHub,url);
    private static HashMap<Long,SubscriptionListener> currentBitsharesListener = new HashMap<>();

    /**
     * Retrieves the data of an account searching by it's id
     *
     * @param accountId The accountId to retrieve
     * @param request The Api request object, to answer this petition
     */
    public static void getAccountById(String accountId, final ApiRequest request){
        WebSocketThread thread = new WebSocketThread(new GetAccounts(accountId, new WitnessResponseListener() {
            @Override
            public void onSuccess(WitnessResponse response) {
                if (response.result.getClass() == ArrayList.class) {
                    List list = (List) response.result;
                    if (list.size() > 0) {
                        if (list.get(0).getClass() == AccountProperties.class) {
                            request.getListener().success(list.get(0),request.getId());
                            return;
                            //TODO answer a crystal model
                        }
                    }
                }
                request.getListener().fail(request.getId());
            }

            @Override
            public void onError(BaseResponse.Error error) {
                request.getListener().fail(request.getId());
            }
        }),url);
        thread.start();
    }

    /**
     * Gets the account ID from an owner or active key
     *
     * @param address The address to retrieve
     * @param request The Api request object, to answer this petition
     */
    public static void getAccountByOwnerOrActiveAddress(Address address, final ApiRequest request){
        WebSocketThread thread = new WebSocketThread(new GetKeyReferences(address, true, new WitnessResponseListener() {
                @Override
                public void onSuccess(WitnessResponse response) {
                    final List<List<UserAccount>> resp = (List<List<UserAccount>>) response.result;
                    if(resp.size() > 0){
                        List<UserAccount> accounts = resp.get(0);
                        if(accounts.size() > 0){
                            for(UserAccount account : accounts) {
                                request.getListener().success(account,request.getId());}}}
                    request.getListener().fail(request.getId());
                }

                @Override
                public void onError(BaseResponse.Error error) {
                    request.getListener().fail(request.getId());
                }
            }),url);

            thread.start();
    }

    /**
     * Gets the Transaction for an Account
     *
     * @param accountGrapheneId The account id to search
     * @param start The start index of the transaction list
     * @param stop The stop index of the transaction list
     * @param limit the maximun transactions to retrieve
     * @param request The Api request object, to answer this petition
     */
    public static void getAccountTransaction(String accountGrapheneId, int start, int stop, int limit, final ApiRequest request){
        WebSocketThread thread = new WebSocketThread(new GetRelativeAccountHistory(new UserAccount(accountGrapheneId), start, limit, stop, new WitnessResponseListener() {
                @Override
                public void onSuccess(WitnessResponse response) {
                    WitnessResponse<List<HistoricalTransfer>> resp = response;
                    request.getListener().success(resp.result,request.getId());
                }

                @Override
                public void onError(BaseResponse.Error error) {
                    request.getListener().fail(request.getId());
                }
            }),url);
            thread.start();
    }

    /**
     * Retrieves the account id by the name of the account
     *
     * @param accountName The account Name to find
     * @param request The Api request object, to answer this petition
     */
    public static void getAccountByName(String accountName, final ApiRequest request){
        WebSocketThread thread = new WebSocketThread(new GetAccountByName(accountName, new WitnessResponseListener() {
            @Override
            public void onSuccess(WitnessResponse response) {
                AccountProperties accountProperties = ((WitnessResponse<AccountProperties>) response).result;
                if(accountProperties == null){
                    request.getListener().fail(request.getId());
                }else{
                    request.getListener().success(accountProperties,request.getId());
                }
            }

            @Override
            public void onError(BaseResponse.Error error) {
                request.getListener().fail(request.getId());
            }
        }),url);
        thread.start();
    }

    /**
     * Retrieves the account id by the name of the account
     *
     * @param accountName The account Name to find
     * @param request The Api request object, to answer this petition
     */
    public static void getAccountIdByName(String accountName, final ApiRequest request){
        WebSocketThread thread = new WebSocketThread(new GetAccountByName(accountName, new WitnessResponseListener() {
                @Override
                public void onSuccess(WitnessResponse response) {
                    AccountProperties accountProperties = ((WitnessResponse<AccountProperties>) response).result;
                    if(accountProperties == null){
                        request.getListener().success(null,request.getId());
                    }else{
                        request.getListener().success(accountProperties.id,request.getId());
                    }
                }

                @Override
                public void onError(BaseResponse.Error error) {
                    request.getListener().fail(request.getId());
                }
            }),url);
        thread.start();
    }

    public static void broadcastTransaction(Transaction transaction, Asset feeAsset, final ApiRequest request){
        WebSocketThread thread = new WebSocketThread(new TransactionBroadcastSequence(transaction, feeAsset, new WitnessResponseListener() {
            @Override
            public void onSuccess(WitnessResponse response) {
                request.getListener().success(true,request.getId());
            }

            @Override
            public void onError(BaseResponse.Error error) {
                request.getListener().fail(request.getId());
            }
        }),url);
        thread.start();
    }

    public static void getAssetByName(ArrayList<String> assetNames, final ApiRequest request){
        //TODO the graphenej library needs a way to creates the Asset object only with the symbol
        ArrayList<Asset> assets = new ArrayList<>();
        for(String assetName : assetNames){
            Asset asset = new Asset("",assetName,-1);
            assets.add(asset);
        }
        //TODO the graphenj library needs to add the lookupAssetSymbols to be able to search the asset by symbol
        // this can be done with the same lookupassetsymbol, but passing only the symbol not the objetcid
        WebSocketThread thread = new WebSocketThread(new LookupAssetSymbols(assets, new WitnessResponseListener() {
            @Override
            public void onSuccess(WitnessResponse response) {
                List<Asset> assets = (List<Asset>) response.result;
                if(assets.size() <= 0){
                    request.getListener().fail(request.getId());
                }else{
                    ArrayList<BitsharesAsset> responseAssets = new ArrayList<>();
                    for(Asset asset: assets){
                        BitsharesAsset.Type assetType = BitsharesAsset.Type.UIA;
                        /*if(asset.getAssetType().equals(Asset.AssetType.CORE_ASSET)){
                            assetType = BitsharesAsset.Type.CORE;
                        }else if(asset.getAssetType().equals(Asset.AssetType.SMART_COIN)){
                            assetType = BitsharesAsset.Type.SMART_COIN;
                        }else if(asset.getAssetType().equals(Asset.AssetType.UIA)){
                            assetType = BitsharesAsset.Type.UIA;
                        }else if(asset.getAssetType().equals(Asset.AssetType.PREDICTION_MARKET)){
                            assetType = BitsharesAsset.Type.PREDICTION_MARKET;
                        }*/
                        BitsharesAsset responseAsset = new BitsharesAsset(asset.getSymbol(),asset.getPrecision(),asset.getObjectId(),assetType);
                        responseAssets.add(responseAsset);
                    }
                    request.getListener().success(responseAssets,request.getId());
                }
            }

            @Override
            public void onError(BaseResponse.Error error) {
                request.getListener().fail(request.getId());
            }
        }),url);
        thread.start();
    }

    public static void getAssetById(ArrayList<String> assetIds, final ApiRequest request){
        ArrayList<Asset> assets = new ArrayList<>();
        for(String assetId : assetIds){
            Asset asset = new Asset(assetId);
            assets.add(asset);
        }

        WebSocketThread thread = new WebSocketThread(new LookupAssetSymbols(assets, new WitnessResponseListener() {
            @Override
            public void onSuccess(WitnessResponse response) {
                List<Asset> assets = (List<Asset>) response.result;
                if(assets.size() <= 0){
                    request.getListener().fail(request.getId());
                }else{
                    ArrayList<BitsharesAsset> responseAssets = new ArrayList<>();
                    for(Asset asset: assets){
                        BitsharesAsset.Type assetType = BitsharesAsset.Type.UIA;
                        /*if(asset.getAssetType().equals(Asset.AssetType.CORE_ASSET)){
                            assetType = BitsharesAsset.Type.CORE;
                        }else if(asset.getAssetType().equals(Asset.AssetType.SMART_COIN)){
                            assetType = BitsharesAsset.Type.SMART_COIN;
                        }else if(asset.getAssetType().equals(Asset.AssetType.UIA)){
                            assetType = BitsharesAsset.Type.UIA;
                        }else if(asset.getAssetType().equals(Asset.AssetType.PREDICTION_MARKET)){
                            assetType = BitsharesAsset.Type.PREDICTION_MARKET;
                        }*/
                        BitsharesAsset responseAsset = new BitsharesAsset(asset.getSymbol(),asset.getPrecision(),asset.getObjectId(),assetType);
                        responseAssets.add(responseAsset);
                    }
                    request.getListener().success(responseAssets,request.getId());
                }
            }

            @Override
            public void onError(BaseResponse.Error error) {
                request.getListener().fail(request.getId());
            }
        }),url);
        thread.start();
    }

    public static void subscribeBitsharesAccount(final long accountId, final String accountBitsharesId, final Context context){
        if(!currentBitsharesListener.containsKey(accountId)){
        CrystalDatabase db = CrystalDatabase.getAppDatabase(context);
        final CryptoCoinBalanceDao balanceDao = db.cryptoCoinBalanceDao();
        final BitsharesAssetDao bitsharesAssetDao = db.bitsharesAssetDao();
        final CryptoCurrencyDao cryptoCurrencyDao = db.cryptoCurrencyDao();
        SubscriptionListener balanceListener = new SubscriptionListener() {
            @Override
            public ObjectType getInterestObjectType() {
                return ObjectType.BALANCE_OBJECT;
            }

            @Override
            public void onSubscriptionUpdate(SubscriptionResponse response) {
                List<Serializable> updatedObjects = (List<Serializable>) response.params.get(1);
                if(updatedObjects.size() > 0){
                    for(Serializable update : updatedObjects){
                        if(update instanceof AccountBalanceUpdate){
                            AccountBalanceUpdate balanceUpdate = (AccountBalanceUpdate) update;
                            if(balanceUpdate.owner.equals(accountBitsharesId)){
                                final CryptoCoinBalance balance = new CryptoCoinBalance();
                                balance.setAccountId(accountId);
                                balance.setBalance(((AccountBalanceUpdate) update).balance);
                                BitsharesAssetInfo assetInfo = bitsharesAssetDao.getBitsharesAssetInfoById(((AccountBalanceUpdate) update).asset_type);
                                if(assetInfo == null ){
                                    final String assetType = ((AccountBalanceUpdate) update).asset_type;
                                    ArrayList<String> idAssets = new ArrayList<>();
                                    idAssets.add(assetType);
                                    ApiRequest getAssetRequest = new ApiRequest(1, new ApiRequestListener() {
                                        @Override
                                        public void success(Object answer, int idPetition) {
                                            if(answer instanceof  BitsharesAsset){
                                                BitsharesAssetInfo info = new BitsharesAssetInfo((BitsharesAsset) answer);
                                                long cryptoCurrencyId = cryptoCurrencyDao.insertCryptoCurrency((CryptoCurrency)answer )[0];
                                                info.setCryptoCurrencyId(cryptoCurrencyId);
                                                bitsharesAssetDao.insertBitsharesAssetInfo(info);
                                                balance.setCryptoCurrencyId(cryptoCurrencyId);
                                                balanceDao.insertCryptoCoinBalance(balance);
                                            }
                                        }

                                        @Override
                                        public void fail(int idPetition) {

                                        }
                                    });
                                    getAssetById(idAssets,getAssetRequest);
                                }else {

                                    balance.setCryptoCurrencyId(assetInfo.getCryptoCurrencyId());
                                    balanceDao.insertCryptoCoinBalance(balance);
                                }
                                BitsharesAccountManager.refreshAccountTransactions(accountId,context);

                            }
                        }
                    }

                }
            }
        };

        currentBitsharesListener.put(accountId,balanceListener);
        bitsharesSubscriptionHub.addSubscriptionListener(balanceListener);

        if(!subscriptionThread.isConnected()){
            subscriptionThread.start();
        }else if(!bitsharesSubscriptionHub.isSubscribed()){
            bitsharesSubscriptionHub.resubscribe();
        }
        }
    }

    public static void cancelBitsharesAccountSubcriptions(){
        bitsharesSubscriptionHub.cancelSubscriptions();
    }

    public static void getAccountBalance(final long accountId, final String accountGrapheneId, final Context context){

        CrystalDatabase db = CrystalDatabase.getAppDatabase(context);
        final CryptoCoinBalanceDao balanceDao = db.cryptoCoinBalanceDao();
        final BitsharesAssetDao bitsharesAssetDao = db.bitsharesAssetDao();
        final CryptoCurrencyDao cryptoCurrencyDao = db.cryptoCurrencyDao();
        WebSocketThread thread = new WebSocketThread(new GetAccountBalances(new UserAccount(accountGrapheneId), new ArrayList<Asset>(), new WitnessResponseListener() {
            @Override
            public void onSuccess(WitnessResponse response) {
                List<AssetAmount> balances = (List<AssetAmount>) response.result;
                for(final AssetAmount balance : balances){
                    final CryptoCoinBalance ccBalance = new CryptoCoinBalance();
                    ccBalance.setAccountId(accountId);
                    ccBalance.setBalance(balance.getAmount().longValue());
                    BitsharesAssetInfo assetInfo = bitsharesAssetDao.getBitsharesAssetInfoById(balance.getAsset().getObjectId());
                    if(assetInfo == null ){
                        ArrayList<String> idAssets = new ArrayList<>();
                        idAssets.add(balance.getAsset().getObjectId());
                        ApiRequest getAssetRequest = new ApiRequest(1, new ApiRequestListener() {
                            @Override
                            public void success(Object answer, int idPetition) {
                                    List<BitsharesAsset> assets = (List<BitsharesAsset>) answer;
                                    for(BitsharesAsset asset : assets) {
                                        BitsharesAssetInfo info = new BitsharesAssetInfo(asset);
                                        long[] cryptoCurrencyId = cryptoCurrencyDao.insertCryptoCurrency((CryptoCurrency) asset);
                                        info.setCryptoCurrencyId(cryptoCurrencyId[0]);
                                        bitsharesAssetDao.insertBitsharesAssetInfo(info);
                                        ccBalance.setCryptoCurrencyId(cryptoCurrencyId[0]);
                                        balanceDao.insertCryptoCoinBalance(ccBalance);
                                    }
                            }

                            @Override
                            public void fail(int idPetition) {
                            }
                        });
                        getAssetById(idAssets,getAssetRequest);

                    }else {

                        ccBalance.setCryptoCurrencyId(assetInfo.getCryptoCurrencyId());
                        balanceDao.insertCryptoCoinBalance(ccBalance);
                    }
                }
            }

            @Override
            public void onError(BaseResponse.Error error) {

            }
        }),url);

        thread.start();

    }

    public static void getBlockHeaderTime(long blockHeader, final ApiRequest request){
        WebSocketThread thread = new WebSocketThread(new GetBlockHeader(blockHeader, new WitnessResponseListener() {
            @Override
            public void onSuccess(WitnessResponse response) {
                if(response == null){
                    request.getListener().fail(request.getId());
                }else {
                    request.getListener().success(response.result, request.getId());
                }
            }

            @Override
            public void onError(BaseResponse.Error error) {
                request.getListener().fail(request.getId());
            }
        }),url);
        thread.start();

    }

    public static void getEquivalentValue(String baseId, String quoteId, final ApiRequest request){
        WebSocketThread thread = new WebSocketThread(new GetLimitOrders(baseId, quoteId, 100, new WitnessResponseListener() {
            @Override
            public void onSuccess(WitnessResponse response) {
                List<LimitOrder> orders = (List<LimitOrder>) response.result;
                for(LimitOrder order : orders){
                    Converter converter = new Converter();
                    double equiValue = converter.getConversionRate(order.getSellPrice(),Converter.BASE_TO_QUOTE);
                    request.getListener().success(equiValue,request.getId());
                    break;
                }
            }

            @Override
            public void onError(BaseResponse.Error error) {
                request.getListener().fail(request.getId());
            }
        }),url);
        thread.start();
    }

}

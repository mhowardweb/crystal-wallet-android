package cy.agorise.crystalwallet.apigenerator.insightapi;

import android.content.Context;

import java.util.Date;

import cy.agorise.crystalwallet.apigenerator.insightapi.models.Txi;
import cy.agorise.crystalwallet.apigenerator.insightapi.models.Vin;
import cy.agorise.crystalwallet.apigenerator.insightapi.models.Vout;
import cy.agorise.crystalwallet.models.GTxIO;
import cy.agorise.crystalwallet.models.GeneralCoinAccount;
import cy.agorise.crystalwallet.models.GeneralCoinAddress;
import cy.agorise.crystalwallet.models.GeneralTransaction;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * CThis class retrieve the data of a single transaction
 */

public class GetTransactionData extends Thread implements Callback<Txi> {
    /**
     * The account to be query
     */
    private final GeneralCoinAccount mAccount;
    /**
     * The transaction txid to be query
     */
    private String mTxId;
    /**
     * The serviceGenerator to call
     */
    private InsightApiServiceGenerator mServiceGenerator;
    /**
     * This app context, used to save on the DB
     */
    private Context mContext;

    private String mServerUrl;
    /**
     * If has to wait for another confirmation
     */
    private boolean mMustWait = false;

    /**
     * Constructor used to query for a transaction with unknown confirmations
     * @param txid The txid of the transaciton to be query
     * @param account The account to be query
     * @param context This app Context
     */
    public GetTransactionData(String txid, GeneralCoinAccount account,String serverUrl, Context context) {
        this(txid, account, serverUrl, context, false);
    }

    /**
     * Consturctor to be used qhen the confirmations of the transaction are known
     * @param txid The txid of the transaciton to be query
     * @param account The account to be query
     * @param context This app Context
     * @param mustWait If there is less confirmation that needed
     */
    public GetTransactionData(String txid, GeneralCoinAccount account,String serverUrl, Context context, boolean mustWait) {
        this.mServerUrl = serverUrl;
        this.mAccount = account;
        this.mTxId= txid;
        this.mServiceGenerator = new InsightApiServiceGenerator(serverUrl);
        this.mContext = context;
        this.mMustWait = mustWait;
    }

    /**
     * Function to start the insight api call
     */
    @Override
    public void run() {
        if (this.mMustWait) {
            //We are waiting for confirmation
            try {
                Thread.sleep(InsightApiConstants.sWaitTime);
            } catch (InterruptedException ignored) {
                //TODO this exception never rises
            }
        }

        InsightApiService service = this.mServiceGenerator.getService(InsightApiService.class);
        Call<Txi> txiCall = service.getTransaction(InsightApiConstants.getPath(this.mAccount.getCryptoCoin()),this.mTxId);
        txiCall.enqueue(this);
    }

    @Override
    public void onResponse(Call<Txi> call, Response<Txi> response) {
        if (response.isSuccessful()) {
            Txi txi = response.body();

            GeneralTransaction transaction = new GeneralTransaction();
            transaction.setAccount(this.mAccount);
            transaction.setTxid(txi.txid);
            transaction.setBlock(txi.blockheight);
            transaction.setDate(new Date(txi.time * 1000));
            transaction.setFee((long) (txi.fee * Math.pow(10,this.mAccount.getCryptoCoin().getPrecision())));
            transaction.setConfirm(txi.confirmations);
            transaction.setType(this.mAccount.getCryptoCoin());
            transaction.setBlockHeight(txi.blockheight);

            for (Vin vin : txi.vin) {
                GTxIO input = new GTxIO();
                input.setAmount((long) (vin.value * Math.pow(10,this.mAccount.getCryptoCoin().getPrecision())));
                input.setTransaction(transaction);
                input.setOut(true);
                input.setType(this.mAccount.getCryptoCoin());
                String addr = vin.addr;
                input.setAddressString(addr);
                input.setIndex(vin.n);
                input.setScriptHex(vin.scriptSig.hex);
                input.setOriginalTxid(vin.txid);
                for (GeneralCoinAddress address : this.mAccount.getAddresses()) {
                    if (address.getAddressString(this.mAccount.getNetworkParam()).equals(addr)) {
                        input.setAddress(address);
                        if (!address.hasTransactionOutput(input, this.mAccount.getNetworkParam())) {
                            address.getTransactionOutput().add(input);
                        }
                    }
                }
                transaction.getTxInputs().add(input);
            }

            for (Vout vout : txi.vout) {
                if(vout.scriptPubKey.addresses == null || vout.scriptPubKey.addresses.length <= 0){
                    // The address is null, this must be a memo
                    String hex = vout.scriptPubKey.hex;
                    int opReturnIndex = hex.indexOf("6a");
                    if(opReturnIndex >= 0) {
                        byte[] memoBytes = new byte[Integer.parseInt(hex.substring(opReturnIndex+2,opReturnIndex+4),16)];
                        for(int i = 0; i < memoBytes.length;i++){
                            memoBytes[i] = Byte.parseByte(hex.substring(opReturnIndex+4+(i*2),opReturnIndex+6+(i*2)),16);
                        }
                        transaction.setMemo(new String(memoBytes));
                        System.out.println("Memo read : " + transaction.getMemo()); //TODO log this line
                    }

                }else {
                    GTxIO output = new GTxIO();
                    output.setAmount((long) (vout.value * Math.pow(10, this.mAccount.getCryptoCoin().getPrecision())));
                    output.setTransaction(transaction);
                    output.setOut(false);
                    output.setType(this.mAccount.getCryptoCoin());
                    String addr = vout.scriptPubKey.addresses[0];
                    output.setAddressString(addr);
                    output.setIndex(vout.n);
                    output.setScriptHex(vout.scriptPubKey.hex);
                    for (GeneralCoinAddress address : this.mAccount.getAddresses()) {
                        if (address.getAddressString(this.mAccount.getNetworkParam()).equals(addr)) {
                            output.setAddress(address);
                            if (!address.hasTransactionInput(output, this.mAccount.getNetworkParam())) {
                                address.getTransactionInput().add(output);
                            }
                        }
                    }
                    transaction.getTxOutputs().add(output);
                }
            }

            // This is for features like dash instantSend
            if(txi.txlock && txi.confirmations< this.mAccount.getCryptoNet().getConfirmationsNeeded()){
                transaction.setConfirm(this.mAccount.getCryptoNet().getConfirmationsNeeded());
            }

            //TODO database
            /*SCWallDatabase db = new SCWallDatabase(this.mContext);
            long idTransaction = db.getGeneralTransactionId(transaction);
            if (idTransaction == -1) {
                db.putGeneralTransaction(transaction);
            } else {
                transaction.setId(idTransaction);
                db.updateGeneralTransaction(transaction);
            }*/

            this.mAccount.updateTransaction(transaction);
            this.mAccount.balanceChange();

            if (transaction.getConfirm() < this.mAccount.getCryptoNet().getConfirmationsNeeded()) {
                //If transaction weren't confirmed, add the transaction to watch for change on the confirmations
                new GetTransactionData(this.mTxId, this.mAccount, this.mServerUrl, this.mContext, true).start();
            }
        }
    }

    /**
     * TODO handle the failure response
     * @param call the Call object
     * @param t the reason of the failure
     */
    @Override
    public void onFailure(Call<Txi> call, Throwable t) {

    }
}

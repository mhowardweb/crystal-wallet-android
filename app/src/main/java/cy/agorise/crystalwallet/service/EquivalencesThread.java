package cy.agorise.crystalwallet.service;

import android.arch.lifecycle.LifecycleService;
import android.util.Log;

import java.util.List;

import cy.agorise.crystalwallet.apigenerator.GrapheneApiGenerator;
import cy.agorise.crystalwallet.models.BitsharesAsset;

/**
 * Created by Henry Varona on 14/11/2017.
 */

public class EquivalencesThread extends Thread{
    private boolean keepLoadingEquivalences = true;
    private LifecycleService service;
    private String fromAsset;
    private List<BitsharesAsset> bitsharesAssets;

    public EquivalencesThread(LifecycleService service, String fromAsset, List<BitsharesAsset> bitsharesAssets){
        this.service = service;
        this.fromAsset = fromAsset;
        this.bitsharesAssets = bitsharesAssets;
    }

    @Override
    public void run() {
        super.run();

        while(this.keepLoadingEquivalences){
            try {
                GrapheneApiGenerator.getEquivalenValue(fromAsset, bitsharesAssets, this.service);
                Log.i("Equivalences Thread", "In loop");
                Thread.sleep(300000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

        }
    }

    public void stopLoadingEquivalences(){
        this.keepLoadingEquivalences = false;
    }
}

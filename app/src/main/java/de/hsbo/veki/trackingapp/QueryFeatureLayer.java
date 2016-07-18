package de.hsbo.veki.trackingapp;

import android.graphics.Color;
import android.os.AsyncTask;
import android.util.Log;

import com.esri.core.map.Feature;
import com.esri.core.map.FeatureResult;
import com.esri.core.map.Graphic;
import com.esri.core.symbol.SimpleMarkerSymbol;
import com.esri.core.tasks.query.QueryParameters;
import com.esri.core.tasks.query.QueryTask;


public class QueryFeatureLayer extends AsyncTask<String, Void, FeatureResult> {


    /**
     * Method to receive current features from feature service
     *
     * @param params - query parameter
     * @return - features
     */
        @Override
        protected FeatureResult doInBackground(String... params) {

            Log.e("UserCred", params[0]);
            String whereClause = "UserID='" + params[0] + "'";

            // Define a new query and set parameters
            QueryParameters mParams = new QueryParameters();
            mParams.setWhere(whereClause);
            mParams.setReturnGeometry(true);

            // Define the new instance of QueryTask
            QueryTask queryTask = new QueryTask(MainActivity.featureLayerURL);
            FeatureResult results;

            try {
                // run the querytask
                results = queryTask.execute(mParams);
                return results;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

    /**
     * Method to add features to graphic layer
     *
     * @param results - features from feature service
     */
        @Override
        protected void onPostExecute(FeatureResult results) {

            // Remove the result from previously run query task
            MainActivity.graphicsLayer.removeAll();

            // Define a new marker symbol for the result graphics
            SimpleMarkerSymbol sms = new SimpleMarkerSymbol(Color.BLUE, 10, SimpleMarkerSymbol.STYLE.CIRCLE);

            // iterate through results
            for (Object element : results) {
                // if object is feature cast to feature
                if (element instanceof Feature) {
                    Feature feature = (Feature) element;
                    // convert feature to graphic
                    Graphic graphic = new Graphic(feature.getGeometry(), sms, feature.getAttributes());
                    // add it to the layer
                    MainActivity.graphicsLayer.addGraphic(graphic);
                }
            }


        }
    }



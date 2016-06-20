package de.hsbo.veki.trackingapp;

import android.graphics.Color;
import android.os.AsyncTask;

import com.esri.android.map.GraphicsLayer;
import com.esri.android.map.MapView;
import com.esri.core.geometry.Envelope;
import com.esri.core.geometry.Point;
import com.esri.core.map.Feature;
import com.esri.core.map.FeatureResult;
import com.esri.core.map.Graphic;
import com.esri.core.symbol.SimpleMarkerSymbol;
import com.esri.core.tasks.query.QueryParameters;
import com.esri.core.tasks.query.QueryTask;

/**
 * Created by Thorsten Kelm on 08.06.2016.
 */
public class QueryFeatureLayer extends AsyncTask<String, Void, FeatureResult> {


        @Override
        protected FeatureResult doInBackground(String... params) {

            String whereClause = "Username='" + params[0] + "'";

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

        @Override
        protected void onPostExecute(FeatureResult results) {

            // Remove the result from previously run query task
            MainActivity.graphicsLayer.removeAll();

            // Define a new marker symbol for the result graphics
            SimpleMarkerSymbol sms = new SimpleMarkerSymbol(Color.GREEN, 10, SimpleMarkerSymbol.STYLE.CIRCLE);

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



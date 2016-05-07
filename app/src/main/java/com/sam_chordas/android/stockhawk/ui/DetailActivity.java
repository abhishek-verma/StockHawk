package com.sam_chordas.android.stockhawk.ui;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPointInterface;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.OnDataPointTapListener;
import com.jjoe64.graphview.series.Series;
import com.sam_chordas.android.stockhawk.R;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class DetailActivity extends Activity {


    private final static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final static DateFormat dateFormatForView = new SimpleDateFormat("MM/dd");
    private static final String LOG_TAG = DetailActivity.class.getSimpleName();

    private final Map<String, Data> dataMap = new HashMap<>();
    private GraphView graph;
    private final DataStats dataStats = new DataStats();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail_activty);


        Intent intent  = getIntent();
        if(intent!= null){
            String stockID = intent.getStringExtra(getString(R.string.symbol));
            if(stockID != null && stockID.trim().length() > 0){
                new FetchStockInfo().execute(stockID);
            }
        }


        LinearLayout layout = (LinearLayout) findViewById(R.id.listItemQuoteLnrLyt);
        initializeStockInfoView(layout);

        graph = (GraphView) findViewById(R.id.stockGrpV);
    }


    private void initializeStockInfoView(LinearLayout linearLayout){
        Intent intent = getIntent();
        String symbol = intent.getStringExtra(getString(R.string.symbol));
        String price = intent.getStringExtra(getString(R.string.price));
        boolean isUp = intent.getBooleanExtra(getString(R.string.isUp), false);
        String change =intent.getStringExtra(getString(R.string.change));

        ((TextView) linearLayout.findViewById(R.id.stockSymbolTxtV)).setText(symbol);
        ((TextView)linearLayout.findViewById(R.id.bidPriceTxtV)).setText(price);
        TextView changeView = ((TextView)linearLayout.findViewById(R.id.changeTxtV));
        changeView.setText(change);

        if(isUp) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                changeView.setBackgroundDrawable(
                        getResources().getDrawable(R.drawable.percent_change_pill_green));
            } else {
                changeView.setBackground(
                        getResources().getDrawable(R.drawable.percent_change_pill_green, null));
            }
        }else{
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                changeView.setBackgroundDrawable(
                        getResources().getDrawable(R.drawable.percent_change_pill_red));
            } else {
                changeView.setBackground(
                        getResources().getDrawable(R.drawable.percent_change_pill_red, null));
            }
        }
    }

    private void initGraphView(){
        Data[] dataPoints = new Data[dataMap.size()];
        dataMap.values().toArray(dataPoints);
        Arrays.sort(dataPoints);

        LineGraphSeries<Data> series = new LineGraphSeries<Data>(dataPoints);
        graph.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter() {
            @Override
            public String formatLabel(double value, boolean isValueX) {
                if (isValueX) {
                    Data data = dataMap.get(((int) value) + "");
                    if (data == null) {
                        return null;
                    } else {
                        return dateFormatForView.format(data.date);
                    }
                } else {
                    return "$" + super.formatLabel(value, isValueX);
                }
            }
        });
        series.setOnDataPointTapListener(new OnDataPointTapListener() {
            @Override
            public void onTap(Series series, DataPointInterface dataPoint) {
                Toast.makeText(DetailActivity.this, "" + dataPoint.getY(), Toast.LENGTH_SHORT).show();
            }
        });
        graph.addSeries(series);
        graph.getViewport().setScalable(true);
        graph.getViewport().setMinX(dataStats.minX);
        graph.getViewport().setMaxX(dataStats.maxX);

    }

    private class FetchStockInfo  extends AsyncTask<String, Void, Boolean> {
        private final OkHttpClient client = new OkHttpClient();

        @Override
        protected Boolean doInBackground(String... params) {
            try {
                String stockID = params[0];
                String jsonRespone = fetchJsonData(stockID);
                Log.i(LOG_TAG, "doInBackground: Value for jsonResponse: " + jsonRespone);
                JSONObject jsonObject = new JSONObject(jsonRespone).getJSONObject("query")
                        .getJSONObject("results");

                JSONArray array = jsonObject.getJSONArray("quote");
                int length = array.length();
                for(int i=0; i<length; i++){
                    JSONObject stockDayData = array.getJSONObject(i);
                    Date date = dateFormat.parse(stockDayData.getString("Date"));
                    double price = Double.parseDouble(stockDayData.getString("Close"));
                    if(i>dataStats.maxX){
                        dataStats.maxX = i;
                    }
                    if(i<dataStats.minX){
                        dataStats.minX = i;
                    }

                    if(price>dataStats.maxY){
                        dataStats.maxY = price;
                    }
                    if(price<dataStats.minY){
                        dataStats.minY = price;
                    }
                    int id = length-1-i;

                    dataMap.put(id+"", new Data(id, date, price));
                }
                return true;
            }catch (Exception e){
                Log.e("Error", e.getMessage(), e);
            }
            return false;
        }


        private String fetchJsonData(String stockID) throws IOException {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE, -1);
            String endDate = dateFormat.format(cal.getTime());  //Yesterdays date is the end date
            cal.add(Calendar.DATE, -30);
            String startDate = dateFormat.format(cal.getTime());  //About one month of data
            String query = "select * from yahoo.finance.historicaldata where symbol = \""
                    +stockID+"\" and startDate = \""
                    +startDate+"\" and endDate = \""+endDate+"\"";
            StringBuilder urlStringBuilder = new StringBuilder();
            urlStringBuilder.append("https://query.yahooapis.com/v1/public/yql?q=");
            urlStringBuilder.append(URLEncoder.encode(query, "UTF-8"));
            urlStringBuilder.append("&format=json&diagnostics=true&env=store%3A%2F%2Fdatatables.org%2Falltableswithkeys&callback=");
            Log.e("To Run", urlStringBuilder.toString());

            Request request = new Request.Builder()
                    .url(urlStringBuilder.toString())
                    .build();

            Response response = client.newCall(request).execute();
            return response.body().string();
        }


        @Override
        protected void onPostExecute(Boolean changed){
            if(changed){
                initGraphView();
            }
        }

    }

    private class DataStats{
        int minX=Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        double minY=Double.MIN_VALUE, maxY= Integer.MAX_VALUE;
    }

    private class Data implements DataPointInterface, Comparable<Data> {
        public final double id;
        public final Date date;
        public final double price;

        public Data(double id, Date date, double price) {
            this.id = id;
            this.date = date;
            this.price = price;
        }

        @Override
        public double getX() {
            return id;
        }

        @Override
        public double getY() {
            return price;
        }


        @Override
        public int compareTo(Data another) {
            return this.date.compareTo(another.date);
        }
    }
}

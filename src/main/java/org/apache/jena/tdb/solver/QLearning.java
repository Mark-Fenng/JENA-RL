/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.tdb.solver;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.OpBGP;
import org.apache.jena.sparql.core.BasicPattern;
import org.apache.jena.sparql.engine.ExecutionContext;
import org.apache.jena.sparql.engine.QueryIterator;
import org.apache.jena.sparql.engine.iterator.QueryIterPeek;
import org.apache.jena.sparql.engine.iterator.QueryIterRoot;

public class QLearning {

    private final double alpha = 0.1; // Learning rate
    private final double gamma = 0.9; // Eagerness - 0 looks in the near future, 1 looks in the distant future

    private int columnLength = 0;

    // private final int reward = 100;
    // private final int penalty = -10;

    private Map<List<Integer>, List<Double>> Q; // Q learning
    private List<Integer> State;
    private ArrayList<Integer> Result;
    private BasicPattern pattern;
    private Op op;
    private QueryIterator input;
    private ExecutionContext execCxt;
    private final String QFile = "./Q.hashmap";

    QLearning(BasicPattern pattern, ExecutionContext execCxt) {
        this.columnLength = pattern.size();
        this.pattern = pattern;
        this.execCxt = execCxt;
        this.State = new ArrayList<>();
        this.Q = new HashMap<>();
        for (int i = 0; i < columnLength; i++)
            State.add(0);
        init();
        this.Q = (Map<List<Integer>, List<Double>>) readFile(this.QFile);
    }

    void initInputIterator() {
        this.input = QueryIterRoot.create(execCxt);
        QueryIterPeek peek = QueryIterPeek.create(this.input, execCxt);
        this.input = peek; // Must pass on
    }

    void init() {
        for (int i = 0; i < columnLength; i++)
            State.set(i, 0);
        Result = new ArrayList<>();
    }

    void calculateQ() {
        Random rand = new Random();

        for (int i = 0; i < 20; i++) { // Train cycles
            System.out.println("Round count: " + i);
            init();
            while (!isFinalState()) {
                initInputIterator();
                List<Integer> actionsFromCurrentState = possibleActionsFromState();
                List<Double> QFromCurrentState = getQFromCurrentState();

                // Pick a random action from the ones possible
                int index = rand.nextInt(actionsFromCurrentState.size());
                int choice = actionsFromCurrentState.get(index);
                State.set(choice, 1);
                Result.add(choice);

                // Q(state,action)= Q(state,action) + alpha * (R(state,action) + gamma *
                // Max(next state, all actions) - Q(state,action))
                double maxQ = maxQ();

                double r = -runQuery();

                double value = alpha * (r + gamma * maxQ);
                QFromCurrentState.set(choice, value);
                System.out.println("State: " + Result.toString());
            }
        }
        writeFile(this.Q, this.QFile);
    }

    boolean isFinalState() {

        return Result.size() == columnLength;
    }

    List<Integer> possibleActionsFromState() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < columnLength; i++)
            if (this.State.get(i) == 0)
                result.add(i);
        return result;
    }

    List<Double> getQFromCurrentState() {
        if (!Q.containsKey(State)) {
            List<Double> newValue = new ArrayList<>();
            for (int i = 0; i < columnLength; i++)
                newValue.add(0.0);
            Q.put(new ArrayList<>(State), newValue);
        }
        return Q.get(State);

    }

    double maxQ() {
        List<Integer> actionsFromState = possibleActionsFromState();
        List<Double> QFromNextState = getQFromCurrentState();

        // the learning rate and eagerness will keep the W value above the lowest reward
        double maxValue = Double.NEGATIVE_INFINITY;
        for (int nextAction : actionsFromState) {
            double value = QFromNextState.get(nextAction);

            if (value > maxValue)
                maxValue = value;
        }
        return maxValue == Double.NEGATIVE_INFINITY ? 0 : maxValue;
    }

    void getPolicy() {
        init();
        printQ();
        while (!isFinalState()) {
            int nextAction = getPolicyFromState();
            State.set(nextAction, 1);
            Result.add(nextAction);
        }
        long costTime = runQuery();
        System.out.println("Policy: " + Result.toString());
        System.out.println("Final time cost: " + costTime);
    }

    int getPolicyFromState() {
        List<Integer> actionsFromState = possibleActionsFromState();
        List<Double> QFromCurrentState = getQFromCurrentState();

        double maxValue = Double.NEGATIVE_INFINITY;
        int policyGotoState = -1;
        // System.out.println(State);
        // System.out.println(QFromCurrentState);

        // Pick to move to the state that has the maximum Q value
        for (int nextState : actionsFromState) {
            double value = QFromCurrentState.get(nextState);
            if (value < 0 && value > maxValue) {
                maxValue = value;
                policyGotoState = nextState;
            }
        }
        return policyGotoState;
    }

    long runQuery() {
        initInputIterator();
        List<Triple> triples = pattern.getList();
        BasicPattern newPattern = new BasicPattern();
        Result.forEach(o -> newPattern.add(triples.get(o)));
        op = new OpBGP(newPattern);
        QueryIterator q = OpExecutorTDB1.plainExecute(op, this.input, execCxt);
        long startTime = System.currentTimeMillis();
        for (; q.hasNext(); q.nextBinding())
            ;
        return System.currentTimeMillis() - startTime;
    }

    void printQ() {
        System.out.println("Q matrix");
        for (Map.Entry<List<Integer>, List<Double>> entry : Q.entrySet()) {
            System.out.println("From state " + entry.getKey().toString() + ": " + entry.getValue().toString());
        }
    }

    public static void writeFile(Object object, String fileName) {
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(fileName);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(object);
            oos.flush();
            oos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Object readFile(String fileName) {
        Object result = null;
        try {
            FileInputStream fis = new FileInputStream(fileName);
            ObjectInputStream ois = new ObjectInputStream(fis);
            result = ois.readObject();
            ois.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return result;
    }
}
/*
 *  Copyright (c) 2016 Joeri de Ruiter
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package nl.cypherpunk.statelearner.sae;
import de.learnlib.api.SUL;
import de.learnlib.logging.LearnLogger;
import net.automatalib.words.Word;
import net.automatalib.words.impl.SimpleAlphabet;
import nl.cypherpunk.statelearner.Learner;
import nl.cypherpunk.statelearner.StateLearnerSUL;
import nl.cypherpunk.statelearner.socket.SocketConfig;

import java.io.*;
import java.lang.reflect.Field;
import java.net.Socket;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;

public class SAESUL implements StateLearnerSUL<String, String> {
	SAEConfig config;
	SimpleAlphabet<String> alphabet;
	Process SAEPython;
	BufferedWriter out;
	BufferedReader in;
	private final Object lock = new Object();
	public SAESUL(SAEConfig config) throws Exception {
		this.config = config;
		alphabet = new SimpleAlphabet<String>(Arrays.asList(config.alphabet.split(" ")));

		// Initialise test service
		ProcessBuilder processBuilder = new ProcessBuilder("sudo","python3", "/home/pirwani/Desktop/wpa3-fsm/sae-learn/Driver.py");
		processBuilder.redirectErrorStream(true);
		SAEPython = processBuilder.start();
		out = new BufferedWriter(new OutputStreamWriter(SAEPython.getOutputStream()));
		in = new BufferedReader(new InputStreamReader(SAEPython.getInputStream()));

	}

	public SimpleAlphabet<String> getAlphabet() {
		return alphabet;
	}

	public String step(String symbol) {
		String result = "";
		try {
			// Process symbol and return result
			// System.out.println("Sending symbol: " + symbol);
//			BufferedReader pipe = new BufferedReader(new FileReader("/home/pirwani/Desktop/wpa3-fsm/statelearner/sync.pipe"));
//			String pythonSignal = pipe.readLine();
//			while (true) {
//				if (pythonSignal.equals("ready")) {
				out.write(symbol + "\n");
				out.flush();
				result = in.readLine();
//				break;
//				}
//			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}
	
	public String queryToString(Word<String> query) {
		StringBuilder builder = new StringBuilder();
		boolean first= true;
		for(String input: query) {
			if(first) {
				first = false;
			} else {
				builder.append(config.delimiter_input);
			}
			builder.append(input);
		}
		return builder.toString();
	}
	
	public Word<String> wordFromResponse(String response) {
		String[] outputs = response.split(config.delimiter_output);
		return Word.fromArray(outputs, 0, outputs.length);
	}

	public Word<String> stepWord(Word<String> query) {
		try {
			synchronized (lock) {
				out.write(queryToString(query)); // Each input in query is separated by
				// space when using .toString()
				out.write("\n");
				out.flush();
			}
			String response = in.readLine();
			return wordFromResponse(response);
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public boolean canFork() {
		return false;
	}

	public SUL<String, String> fork() throws UnsupportedOperationException {
		throw new UnsupportedOperationException("Cannot fork SocketSUL");
	}

	public void pre() {
		try {
			if (!config.combine_query) {
				// Reset test service
//				System.out.println("Sending symbol: RESET");
				out.write("RESET\n");
				out.flush();
	//			in.readLine();
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public void post() {
		// Nothing to cleanup
	}
}
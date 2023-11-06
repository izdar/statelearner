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

package nl.cypherpunk.statelearner;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;

import javax.annotation.ParametersAreNonnullByDefault;

import de.learnlib.api.MembershipOracle;
import de.learnlib.api.MembershipOracle.MealyMembershipOracle;
import de.learnlib.api.Query;
import de.learnlib.logging.LearnLogger;
import net.automatalib.words.Word;
import net.automatalib.words.WordBuilder;

// Based on SULOracle from LearnLib by Falk Howar and Malte Isberner
@ParametersAreNonnullByDefault
public class LogOracle<I, D> implements MealyMembershipOracle<I,D> {
	public static class MealyLogOracle<I,O> extends LogOracle<I,O> {
		public MealyLogOracle(StateLearnerSUL<I, O> sul, LearnLogger logger, boolean combine_query) {
			super(sul, logger, combine_query);
		}
	}
	
	LearnLogger logger;
	StateLearnerSUL<I, D> sul;

	private final Map<String, Word<D>> queryCache;
	boolean combine_query = false;
   
    public LogOracle(StateLearnerSUL<I, D> sul, LearnLogger logger, boolean combine_query) {
        this.sul = sul;
		this.queryCache = new HashMap<>();
        this.logger = logger;
        this.combine_query = combine_query;
    }
    
    public Word<D> answerQueryCombined(Word<I> prefix, Word<I> suffix) {
		Word<I> query = prefix.concat(suffix);
		Word<D> response = null;
		Word<D> responsePrefix = null;
		Word<D> responseSuffix = null;

		try {
			this.sul.pre();
			response = this.sul.stepWord(query);

			if(query.length() != response.length()) {
				throw new RuntimeException("Received number of output symbols not equal to number of input symbols (" + query.length() + " input symbols vs " + response.length() + " output symbols)");
			}

			responsePrefix = response.subWord(0, prefix.length());
			responseSuffix = response.subWord(prefix.length(), response.length());

			logger.logQuery("[" + prefix.toString() + " | " + suffix.toString() + " / " + responsePrefix.toString() + " | " + responseSuffix.toString() + "]");
		} finally {
			sul.post();
		}

		// Only return the responses to the suffix
		return responseSuffix;
    }

	public Word<D> answerQuerySteps(Word<I> prefix, Word<I> suffix) {
		WordBuilder<D> wbSuffix = new WordBuilder<>(suffix.length());
		WordBuilder<D> wbPrefix = new WordBuilder<>(prefix.length());

		try {
			Map<Word<D>, Integer> responseCounts = new HashMap<>();
			int reruns = 0;
			int maxReruns = 3;
			while (reruns < maxReruns) {
				this.sul.pre();
				wbPrefix.clear();
				wbSuffix.clear();

				for (I sym : prefix) {
					wbPrefix.add(this.sul.step(sym));
				}

				for (I sym : suffix) {
					D response = this.sul.step(sym);
					wbSuffix.add(response);
				}

				Word<D> responseWord = wbPrefix.toWord().concat(wbSuffix.toWord());

				int count = responseCounts.getOrDefault(responseWord, 0) + 1;
				responseCounts.put(responseWord, count);

				reruns++;
			}

			Word<D> mostCommonResponse = null;
			int mostCommonResponseCount = 0;

			for (Map.Entry<Word<D>, Integer> entry : responseCounts.entrySet()) {
				if (entry.getValue() > mostCommonResponseCount) {
					mostCommonResponse = entry.getKey();
					mostCommonResponseCount = entry.getValue();
				}
			}
			logger.logQuery("[" + prefix.toString() + " | " + suffix.toString() + " / " + wbPrefix.toWord().toString() + " | " + wbSuffix.toWord().toString() + "]");
			return mostCommonResponse.subWord(prefix.length(), mostCommonResponse.length());
		} finally {
			sul.post();
		}
	}





	@Override
	public Word<D> answerQuery(Word<I> prefix, Word<I> suffix) {
		if(combine_query) {
			return answerQueryCombined(prefix, suffix);
		} else {
			String cacheKey = prefix.toString() + "|" + suffix.toString();
			if (this.queryCache.containsKey(cacheKey)) {
				// Return cached result if available
				System.out.println("Found in cache: " + cacheKey + ": "+ this.queryCache.get(cacheKey));
				return this.queryCache.get(cacheKey);
			}
			Word<D> result = answerQuerySteps(prefix, suffix);
			this.queryCache.put(cacheKey, result);
			return result;
		}
    }
    
	@Override
    @SuppressWarnings("unchecked")
	public Word<D> answerQuery(Word<I> query) {
		return answerQuery((Word<I>)Word.epsilon(), query);
    }

    @Override
    public MembershipOracle<I, Word<D>> asOracle() {
    	return this;
    }

	@Override

	public void processQueries(Collection<? extends Query<I, Word<D>>> queries) {
		for (Query<I,Word<D>> q : queries) {
			Word<D> output = answerQuery(q.getPrefix(), q.getSuffix());
			q.answer(output);
		}
	}
}

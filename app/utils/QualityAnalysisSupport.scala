package utils

import play.api.Logger


/**
  * Defines a set of functions for analysing the quality of text.
  */
object QualityAnalysisSupport extends Serializable {

  /** Count the numbers of punctuation characters in a string.
   *
   * @param text The input string
   * @return A Map of the basic punctuation characters to the number of times they appear in the input string.
   *         If the punctuation character is not included in the result then it is not contained
   *         within the text.
   */
  def getPunctuationCounts(text: String): Map[Char, Int] = {
    var counts = Map[Char, Int]().withDefaultValue(0)
    text.foreach(c => {
      if (StringUtilities.BasicPunctuation.contains(c)) {
        counts.get(c) match {
          case Some(count) => counts += (c -> (count + 1))
          case None => counts += (c -> 0)
        }
      }
    })
    counts
  }

  def countWords(text: String): Int = {
    text.split(" ").length
  }

  /** Count the number of capitalised words in a string.
   *
   * @param text The input string
   * @return The number of words written entirely in capital letters within the input string.
   */
  def countCapitalisedWords(text: String): Int = {
    var count = 0
    text.split(" ").foreach(word => {
      val uppers = word.filter(_.isUpper)
      if (uppers.length == word.length) {
        count += 1
      }
    })
    count
  }

}

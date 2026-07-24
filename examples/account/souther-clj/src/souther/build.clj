(ns souther.build
  "Build-time helper: run the Souther annotation processor to generate the classes for a `.sou`
   module, without Maven. This drives javac through the JDK compiler API (`javax.tools`), so the
   only thing the caller must provide is the `souther-compiler` jar on the invoking classpath --
   typically via a deps.edn alias -- where the processor is discovered by ServiceLoader.

   Unlike the rest of souther-clj this namespace is not used at runtime; it depends on nothing
   beyond the JDK (it never imports the processor class, only names it to javac)."
  (:import [javax.tools ToolProvider StandardLocation]
           [java.io File]))

(defn- java-triggers
  "javac only runs annotation processing when it has at least one source to compile; a Souther
   module keeps a minimal package-info.java for exactly this. Collect the .java files under dir."
  [dir]
  (->> (file-seq (File. ^String dir))
       (filter #(and (.isFile ^File %) (.endsWith (.getName ^File %) ".java")))))

(defn generate!
  "Compile the `.sou` files in :source-dir into :class-dir by running SoutherProcessor over the
   :trigger-dir Java sources. Defaults match the example layout. Returns :class-dir on success and
   throws on failure. Requires a JDK (a system Java compiler) and souther-compiler on the classpath."
  [{:keys [source-dir trigger-dir class-dir]
    :or {source-dir "src/main/souther"
         trigger-dir "src/main/java"
         class-dir "target/classes"}}]
  (let [compiler (or (ToolProvider/getSystemJavaCompiler)
                     (throw (ex-info "no system Java compiler on this JVM (a JDK is required)" {})))
        file-manager (.getStandardFileManager compiler nil nil nil)
        out (doto (File. ^String class-dir) (.mkdirs))
        triggers (java-triggers trigger-dir)]
    (when (empty? triggers)
      (throw (ex-info "no .java trigger found; javac will not run annotation processing"
                      {:trigger-dir trigger-dir})))
    (.setLocation file-manager StandardLocation/CLASS_OUTPUT [out])
    (let [units (.getJavaFileObjectsFromFiles file-manager triggers)
          options ["-proc:full"                                  ; force annotation processing
                   (str "-Asouther.source=" source-dir)]
          task (.getTask compiler nil file-manager nil options nil units)]
      (when-not (.call task)
        (throw (ex-info "Souther generation failed" {:source-dir source-dir})))
      (println "Souther: generated classes ->" class-dir)
      class-dir)))

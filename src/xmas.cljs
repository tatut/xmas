(ns xmas
  (:require [clojure.string :refer [replace]]
            [figwheel.client :as fw]))

(enable-console-print!)

(defonce canvas nil)
(defonce ctx nil)

(def +width+ 1000)
(def +height+ 600)
(def +ratio+ (/ 600 1000))

(def +half-pi+ (/ Math/PI 2.0)) ; 90 degrees
(def +pi+ Math/PI) ; 180 degrees
(def +two-pi+ (* 2 +pi+)) ; 360 degrees

(defn elt [id]
  (-> js/document (.getElementById id)))

(defn #^:export resize []
  (let [new-width (.-innerWidth js/window)
        new-height (int (* +ratio+ new-width))]
    (.setAttribute canvas
                   "style"
                   (str "width: " new-width "px; "
                        "height: " new-height "px; "))
    (.setTimeout js/window
                 #(.scrollTo js/window 0 1) 1)
    ))

(defn init []
  "Initialize page."
  (set! canvas (.getElementById js/document "xmas1"))

  (set! (.-width canvas) 1000)
  (set! (.-height canvas) 600)
  (set! ctx (.getContext canvas "2d"))
  (resize))




(defn clear []
  (set! (.-fillStyle ctx) "black")
  (.fillRect ctx 0 0 +width+ +height+))



(defn ground-level []
  "Create a random ground level. Returns a sequence of Y values."
  (loop [i 0
         deg (* Math/PI (rand))
         deg2 (* Math/PI (rand))
         speed (+ 0.01 (/ (rand-int 3) 150.0))
         speed2 (+ 0.02 (/ (rand-int 5) 150.0))
         
         acc []]
    (if (= i 1000)
      acc
      (let [height (+ 30 (* 10 (Math/sin deg2)))] 
        (recur (inc i)
               (+ deg speed)
               (+ deg2 speed2)
               speed
               speed2
               (conj acc (int (+ 450 (* height (Math/cos deg))))))))))


    
(defn make-ground [ground-level]
  "Make ground with the given level. The level is a vector of heights of +width+ elements."
  (let [ground-ctx (-> (elt "ground")
                       (.getContext "2d"))]
    (.drawImage ground-ctx (elt "image-ground1") 0 0)
    (let [;; the ground image data
          g (.getImageData ground-ctx 0 0 128 128)
          gd (.-data g)
          ;; target image data to draw
          c (.getImageData ctx 0 0 +width+ +height+)
          cd (.-data c)

          ]
      (dotimes [i (* +width+ +height+)]
        (aset cd (+ (* i 4) 3) 0))

      ;; now we loop through image data to create the ground
      (loop [x 0
             gx 0]
        ;;(.log js/console "x: " x ", gx: " gx)
        (when (< x 1000)
          (loop [y (nth ground-level x)
                 gy 0]
            (when (< y +height+)
              (let [pos (* 4 (+ x (* y +width+)))
                    gpos (* 4 (+ gx (* 128 (if (< gy 6) gy (+ 6 (rem y 122))))))] ;;(* gy 128)))]
                ;;(.log js/console "foo: " (aget gd gpos))
                (aset cd (+ 0 pos) (aget gd (+ 0 gpos)))
                (aset cd (+ 1 pos) (aget gd (+ 1 gpos)))
                (aset cd (+ 2 pos) (aget gd (+ 2 gpos)))
                (aset cd (+ 3 pos) (aget gd (+ 3 gpos)))
                (recur (+ y 1) (+ gy 1)))))
          (recur (+ x 1) (if (= gx 127) 0 (+ gx 1)))))

      
      (let [buffer (elt "buffer")
            buf-ctx (.getContext buffer "2d")
            img (js/Image.)]
        (.putImageData buf-ctx c 0 0)
        buffer))))



(defonce ground nil)
(defonce skybg nil)


(defrecord Snowflake [x y radius opacity])

(defonce snowflakes (atom []))

(defonce greeting (atom ""))
(defn set-greeting! []
  (reset! greeting 
          (-> js/document
              .-location .-hash
              (.substring 1)
              js/decodeURIComponent
              (replace "_" " "))))
      

(defn inside-world? [x y]
  (and (>= x 0)
       (< x +width+)
       ;(>= y 0)
       (< y +height+)
       ))

(defn touches-ground? [ground x y]
  (let [data (.-data (.getImageData (.getContext ground "2d") x y 1 1))
        pos (* 4 (+ x (* y +width+)))
        a (aget data 3)]
    (if (and (not= a 0) (not= a 255))
      ;; if ground alpha is not 0 or 255, allow pass through (for a gradual snow accumulation)
      false
      (not (= 0 
              (aget data 0)
              (aget data 1)
              (aget data 2))))))

(defn new-random-snowflake []
  (->Snowflake (rand +width+)
               (- (rand +height+))
               (+ 3 (rand 4)) (/ (+ 50 (rand 50)) 100.0)))
  
(defn update-snowflakes [snowflakes]
  "Update all snowflakes in the world."
  (loop [acc []
         [{:keys [x y radius opacity] :as sf} & snowflakes] snowflakes]
    (if-not x
      acc
      ;; If snowflakes touches the ground layer, draw it there and reset
      (if (touches-ground? ground x y)
        (do
          ;; render with opacity 0.2 so a place becomes "solid ground"
          ;; only when it has 5 flakes on it, this makes snow
          ;; accumulation much smoother
          (let [gc (.getContext ground "2d")]
            (set! (.-fillStyle gc) "white")
            (set! (.-globalAlpha gc) 0.2)
            (set! (.-globalCompositeOperation gc) "lighter")
            (doto gc
              .beginPath
              (.arc x y 3 0 (* 2 Math/PI))
              .fill))
          (recur (conj acc (new-random-snowflake))
                 snowflakes))

        ;; doesn't touch ground, fall it down
        (recur (conj acc (assoc sf
                           :x (+ x -1 (rand 2))
                           :y (+ 2 y)))
               snowflakes)))))



(defn draw-snowflakes [snowflakes]
  (set! (.-fillStyle ctx) "white")
  (doseq [{:keys [x y radius opacity]} snowflakes]
    (set! (.-globalAlpha ctx) opacity)
    (doto ctx
      .beginPath
      (.arc x y radius 0 (* 2 Math/PI))
      .fill)))


;; t and N are magic values for santa movement
(defonce t (atom 1))
(defonce N (atom 2))
(defonce last-santa (atom nil))

;; Render/update 
(defn render [ts]
  (clear)

  (set! (.-globalAlpha ctx) 1.0)
  
  ;; draw background images
  (.drawImage ctx (elt "bg00") 0 0 +width+ +height+)
  (.drawImage ctx (elt "bg01") 0 0 +width+ +height+)

  ;; Draw sky background parallax
  (let [skyx (rem (/ ts -400) +width+)]
    ;;(.log js/console "skyx: " skyx)
    (.drawImage ctx skybg skyx 0 +width+ 91)
    (.drawImage ctx skybg (+ +width+ skyx) 0 +width+ 91)
    )

  ;; Draw the ground
  (.drawImage ctx ground 0 0)

  ;; Draw the greeting text
  (set! (.-font ctx) "36px 'Mountains of Christmas', cursive")
  (set! (.-fillStyle ctx) "red")
  (let [string @greeting
        width (.-width (.measureText ctx string))]
    (.fillText ctx string
               (- 500 (/ width 2)) 150)) 

  ;; Update and draw all snowflakes
  (-> snowflakes
      (swap! update-snowflakes)
      draw-snowflakes)

  ;; Draw santa flying around
  (let [t (swap! t #(+ % 0.007))
        N (swap! N #(+ % 0.1))
        xa (+ t (* (/ (- N 1) N) +half-pi+))
        ya (* 2 t)
        x (+ 500 (* 900 (Math/sin xa)))
        y (+ 200 (* 100 (Math/cos ya)))
        [lx ly] @last-santa
        sa (Math/atan (/ (- y ly) (- x lx)))]
    (reset! last-santa [x y])
    (set! (.-globalAlpha ctx) 1)
    (set! (.-fillStyle ctx) "red")
    (.save ctx)
    (.translate ctx x y)
    (when (< lx x)
      (.scale ctx -1 1))
    (.rotate ctx sa)
    (.drawImage ctx (elt "santa")
                0 0)
    (.restore ctx))

  ;; Do it again
  (.requestAnimationFrame js/window render))


(defn ^:export start []
  
  (init)
  (clear)
  (set! ground (make-ground (ground-level)))
  (set! skybg (elt "skybg"))
  (set-greeting!)
  (reset! snowflakes
          (mapv (fn [i]
                  (new-random-snowflake))
                (range 100)))
  
  (.requestAnimationFrame js/window render))

(when (= "localhost" (-> js/document .-location .-hostname))
  (fw/start {:on-jsload (fn []
                          (print "Merry Christmas!"))}))





 

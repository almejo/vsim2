package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.function.DoubleUnaryOperator;

/**
 * One animated move of the view (find bar «viaje suave»), as a pure function of the elapsed time. A target within
 * about one screen is an eased pan of {@value #PAN_MILLISECONDS} ms; a farther one is a «fly to» like a map: the view
 * zooms out while it pans until both places show, then zooms back in as it arrives (van Wijk and Nuij's optimal path,
 * the one d3 uses, so the motion on screen keeps a steady pace however far it goes). Both start and end gently and
 * never pass the target; a travel that takes over a view already moving starts at speed (ease-out) instead.
 */
final class ViewTravel {

    static final long PAN_MILLISECONDS = 300;
    static final long MINIMUM_FLIGHT_MILLISECONDS = 500;
    static final long MAXIMUM_FLIGHT_MILLISECONDS = 700;

    /** How much a flight zooms out for its length: √2, the value van Wijk and Nuij found most comfortable. */
    private static final double RHO = Math.sqrt(2);
    private static final double RHO_SQUARED = 2;
    private static final double RHO_FOURTH = 4;
    private static final double SAME_PLACE = 1e-12;

    private final ViewState from;
    private final ViewState to;
    private final long durationMilliseconds;
    private final boolean flies;
    private final DoubleUnaryOperator easing;
    private final Path path;

    /** The view at {@code progress} (0 = start, 1 = target) along the path, before easing. */
    private interface Path {
        ViewState at(double progress);
    }

    private ViewTravel(ViewState from, ViewState to, long durationMilliseconds, boolean flies,
                       DoubleUnaryOperator easing, Path path) {
        this.from = from;
        this.to = to;
        this.durationMilliseconds = durationMilliseconds;
        this.flies = flies;
        this.easing = easing;
        this.path = path;
    }

    /**
     * The travel from {@code from} to {@code to} on a canvas of the given size in pixels. {@code takingOver}: the view
     * is already moving (a new target mid-flight), so the travel starts at speed instead of from rest.
     */
    static ViewTravel between(ViewState from, ViewState to, double screenWidth, double screenHeight,
                              boolean takingOver) {
        double deltaX = to.centerX() - from.centerX();
        double deltaY = to.centerY() - from.centerY();
        DoubleUnaryOperator easing = takingOver ? ViewTravel::easeOut : ViewTravel::easeInOut;
        if (deltaX * deltaX + deltaY * deltaY < SAME_PLACE && from.zoom() == to.zoom()) {
            return new ViewTravel(from, to, 0, false, easing, progress -> to);
        }
        double pixelsPerUnit = GridPoint.PIXELS_PER_UNIT * from.zoom();
        boolean near = Math.abs(deltaX) <= screenWidth / pixelsPerUnit
                && Math.abs(deltaY) <= screenHeight / pixelsPerUnit;
        if (near) {
            return new ViewTravel(from, to, PAN_MILLISECONDS, false, easing, pan(from, to));
        }
        double shortSide = Math.max(1, Math.min(screenWidth, screenHeight));
        Flight flight = new Flight(from, to, shortSide);
        long duration = Math.round(Math.max(MINIMUM_FLIGHT_MILLISECONDS,
                Math.min(MAXIMUM_FLIGHT_MILLISECONDS, 400 + 100 * Math.abs(flight.length))));
        return new ViewTravel(from, to, duration, true, easing, flight::at);
    }

    /** A straight pan; a zoom change, if any, goes evenly in scale (each frame the same ratio). */
    private static Path pan(ViewState from, ViewState to) {
        return progress -> new ViewState(
                from.zoom() * Math.pow(to.zoom() / from.zoom(), progress),
                from.centerX() + (to.centerX() - from.centerX()) * progress,
                from.centerY() + (to.centerY() - from.centerY()) * progress);
    }

    /**
     * van Wijk and Nuij, «Smooth and efficient zooming and panning» (2003), as in d3-interpolate's zoom: the view's
     * width {@code w} (grid units across the short side of the canvas) and its centre follow the path that keeps the
     * on-screen speed constant. {@link #length} is the path's length in their units.
     */
    private static final class Flight {
        private final ViewState from;
        private final double deltaX;
        private final double deltaY;
        private final double shortSide;
        private final double startWidth;
        private final double distance;
        private final double startR;
        private final double length;
        private final boolean straightZoom;

        Flight(ViewState from, ViewState to, double shortSide) {
            this.from = from;
            this.shortSide = shortSide;
            this.deltaX = to.centerX() - from.centerX();
            this.deltaY = to.centerY() - from.centerY();
            this.startWidth = width(from.zoom());
            double endWidth = width(to.zoom());
            double squaredDistance = deltaX * deltaX + deltaY * deltaY;
            this.distance = Math.sqrt(squaredDistance);
            if (squaredDistance < SAME_PLACE) {
                straightZoom = true;
                startR = 0;
                length = Math.log(endWidth / startWidth) / RHO;
            } else {
                straightZoom = false;
                double startB = (endWidth * endWidth - startWidth * startWidth + RHO_FOURTH * squaredDistance)
                        / (2 * startWidth * RHO_SQUARED * distance);
                double endB = (endWidth * endWidth - startWidth * startWidth - RHO_FOURTH * squaredDistance)
                        / (2 * endWidth * RHO_SQUARED * distance);
                startR = Math.log(Math.sqrt(startB * startB + 1) - startB);
                double endR = Math.log(Math.sqrt(endB * endB + 1) - endB);
                length = (endR - startR) / RHO;
            }
        }

        private double width(double zoom) {
            return shortSide / (GridPoint.PIXELS_PER_UNIT * zoom);
        }

        private double zoom(double width) {
            return shortSide / (GridPoint.PIXELS_PER_UNIT * width);
        }

        ViewState at(double progress) {
            double arc = progress * length;
            if (straightZoom) {
                return new ViewState(zoom(startWidth * Math.exp(RHO * arc)), from.centerX() + deltaX * progress,
                        from.centerY() + deltaY * progress);
            }
            double coshStart = Math.cosh(startR);
            double travelled = startWidth / (RHO_SQUARED * distance)
                    * (coshStart * Math.tanh(RHO * arc + startR) - Math.sinh(startR));
            double width = startWidth * coshStart / Math.cosh(RHO * arc + startR);
            return new ViewState(zoom(width), from.centerX() + deltaX * travelled,
                    from.centerY() + deltaY * travelled);
        }
    }

    /**
     * Sine ease-in-out: leaves and arrives at rest, fastest half way at only π/2 times the average speed (a cubic
     * curve peaks at three times), so even a one-screen pan moves a moderate step per 16 ms frame and reads as motion.
     */
    static double easeInOut(double time) {
        return (1 - Math.cos(Math.PI * time)) / 2;
    }

    /** Sine ease-out: starts at the speed an eased pan has at its fastest (no jolt when retargeting), ends at rest. */
    static double easeOut(double time) {
        return Math.sin(Math.PI * time / 2);
    }

    long durationMilliseconds() {
        return durationMilliseconds;
    }

    boolean flies() {
        return flies;
    }

    ViewState target() {
        return to;
    }

    ViewState start() {
        return from;
    }

    /** The view {@code elapsedMilliseconds} after the start; exactly the target from the end on. */
    ViewState at(long elapsedMilliseconds) {
        if (elapsedMilliseconds >= durationMilliseconds) {
            return to;
        }
        if (elapsedMilliseconds <= 0) {
            return from;
        }
        return path.at(easing.applyAsDouble((double) elapsedMilliseconds / durationMilliseconds));
    }
}

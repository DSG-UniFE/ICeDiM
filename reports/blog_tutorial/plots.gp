set terminal postscript eps enhanced color solid colortext 9
set output 'del_ratio.png'

set style fill pattern border
set style histogram errorbars gap 1 lw 3.5
set style data histograms
set boxwidth 0.9
set bars 1


set xtics font "Times-Roman, 17"
set ytics 0.2 font "Times-Roman, 17"

set xlabel "Area (sq m)" font "Times-Roman, 18"
set ylabel "Delivery ratio" font "Times-Roman, 18.5"

#set xrange[-0.5:2.5]

plot 'data.in' using 2:3:xtic(1) index 0 title "Epidemic" fs pattern 0 lt 3 lw 2, \
	'' using 2:3:xtic(1) index 1 title "SnW" fs pattern 1 lt 4 lw 2
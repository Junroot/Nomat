interface NavigationContent extends React.PropsWithChildren {}

const NavigationBar: React.FC<NavigationContent> = ({children}) => {
     return <div
     className="h-full w-[112px] px-[8px] py-[16px] flex flex-col items-center"
    >
        {children}
    </div>
}

export default NavigationBar